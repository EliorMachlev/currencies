package de.salomax.currencies.model.provider

import android.content.Context
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import com.squareup.moshi.Moshi
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.BankOfIsraelObservation
import de.salomax.currencies.model.adapter.BankOfIsraelRatesAdapter
import de.salomax.currencies.model.adapter.BankOfIsraelSdmxParser
import de.salomax.currencies.model.adapter.addFokFromDkkIfMissing
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// SDMX-JSON representative-exchange-rate series live on a separate host from
// the simpler PublicApi endpoint used for live rates.
private const val SDMX_BASE_URL =
    "https://edge.boi.gov.il/FusionEdgeServer/sdmx/v2/data/dataflow/BOI.STATISTICS/EXR/1.0"
private const val SDMX_FORMAT = "sdmx-json"

// Bank of Israel quotes JPY per 100 units and LBP per 10 units in both the
// PublicApi and SDMX feeds; every other currency is quoted per single unit.
private val UNIT_PER_CURRENCY: Map<String, BigDecimal> = mapOf(
    "JPY" to BigDecimal("100"),
    "LBP" to BigDecimal("10"),
)

private fun unitFor(currency: String): BigDecimal =
    UNIT_PER_CURRENCY[currency] ?: BigDecimal.ONE

class BankOfIsrael : ApiProvider.Api() {

    override val name = "Bank of Israel"

    override fun descriptionShort(context: Context) =
        context.getText(R.string.api_bankOfIsrael_descriptionShort)

    override fun getDescriptionLong(context: Context) =
        context.getText(R.string.api_bankOfIsrael_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) =
        context.getText(R.string.api_bankOfIsrael_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = null

    override val baseUrl = "https://boi.org.il"

    override suspend fun getRates(context: Context?, date: LocalDate?): Result<ExchangeRates, FuelError> {
        return if (date == null) fetchLatestRates() else fetchHistoricalRates(date)
    }

    private suspend fun fetchLatestRates(): Result<ExchangeRates, FuelError> =
        Fuel.get("$baseUrl/PublicApi/GetExchangeRates").awaitResult(
            moshiDeserializerOf(
                Moshi.Builder()
                    .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
                    .add(BankOfIsraelRatesAdapter())
                    .build()
                    .adapter(ExchangeRates::class.java)
            )
        ).map { it.copy(provider = ApiProvider.BANK_OF_ISRAEL) }

    private suspend fun fetchHistoricalRates(date: LocalDate): Result<ExchangeRates, FuelError> {
        return Fuel.get(sdmxUrl(date, date)).awaitResult(sdmxObservationsDeserializer())
            .map { observations ->
                val latestPerCurrency = latestObservationPerCurrency(observations)
                val rates = buildIlsRateList(latestPerCurrency.values)
                ExchangeRates(
                    success = rates.isNotEmpty(),
                    error = if (rates.isEmpty()) "No data found." else null,
                    base = Currency.ILS,
                    date = latestPerCurrency.values.maxOfOrNull { it.date },
                    rates = rates,
                    provider = ApiProvider.BANK_OF_ISRAEL,
                )
            }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline, FuelError> {
        return Fuel.get(sdmxUrl(startDate, endDate)).awaitResult(sdmxObservationsDeserializer())
            .map { observations -> buildTimeline(observations, base, symbol) }
    }

    private fun buildTimeline(
        observations: List<BankOfIsraelObservation>,
        base: Currency,
        symbol: Currency,
    ): Timeline {
        val ilsPerForeignByDate = observations.groupBy { it.currency }
            .mapValues { (_, obs) ->
                obs.associate { it.date to it.rawValue.divide(unitFor(it.currency), MathContext.DECIMAL128) }
            }
        val baseCode = base.iso4217Alpha()
        val symbolCode = symbol.iso4217Alpha()

        val allDates = ilsPerForeignByDate.values.flatMap { it.keys }.toSortedSet()
        val rates = sortedMapOf<LocalDate, Rate>()
        for (date in allDates) {
            val ilsPerBase = ilsPerFor(baseCode, date, ilsPerForeignByDate) ?: continue
            val ilsPerSymbol = ilsPerFor(symbolCode, date, ilsPerForeignByDate) ?: continue
            if (ilsPerSymbol.signum() == 0) continue
            // 1 base = ilsPerBase ILS = ilsPerBase / ilsPerSymbol of symbol.
            val ratio = ilsPerBase.divide(ilsPerSymbol, MathContext.DECIMAL128)
            rates[date] = Rate(symbol, ratio)
        }

        return Timeline(
            success = rates.isNotEmpty(),
            error = if (rates.isEmpty()) "No data found." else null,
            base = baseCode,
            startDate = rates.keys.firstOrNull(),
            endDate = rates.keys.lastOrNull(),
            rates = rates,
            provider = ApiProvider.BANK_OF_ISRAEL,
        )
    }

    // How many ILS one unit of [currencyCode] was worth on [date]. ILS→1.
    private fun ilsPerFor(
        currencyCode: String,
        date: LocalDate,
        ilsPerForeignByDate: Map<String, Map<LocalDate, BigDecimal>>,
    ): BigDecimal? {
        if (currencyCode == Currency.ILS.iso4217Alpha()) return BigDecimal.ONE
        return ilsPerForeignByDate[currencyCode]?.get(date)
    }

    private fun latestObservationPerCurrency(
        observations: List<BankOfIsraelObservation>,
    ): Map<String, BankOfIsraelObservation> =
        observations.groupBy { it.currency }
            .mapValues { (_, list) -> list.maxBy { it.date } }

    private fun buildIlsRateList(latest: Collection<BankOfIsraelObservation>): List<Rate> {
        val rates = mutableListOf<Rate>()
        for (obs in latest) {
            val currency = Currency.fromString(obs.currency) ?: continue
            if (obs.rawValue.signum() <= 0) continue
            rates.add(Rate(currency, unitFor(obs.currency).divide(obs.rawValue, MathContext.DECIMAL128)))
        }
        if (rates.isNotEmpty()) {
            rates.add(Rate(Currency.ILS, BigDecimal.ONE))
            rates.addFokFromDkkIfMissing()
        }
        return rates
    }

    private fun sdmxUrl(startDate: LocalDate, endDate: LocalDate): String {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        return "$SDMX_BASE_URL" +
            "?startPeriod=${startDate.format(fmt)}" +
            "&endPeriod=${endDate.format(fmt)}" +
            "&format=$SDMX_FORMAT"
    }

    private fun sdmxObservationsDeserializer() =
        object : ResponseDeserializable<List<BankOfIsraelObservation>> {
            override fun deserialize(inputStream: InputStream): List<BankOfIsraelObservation> =
                BankOfIsraelSdmxParser().parse(inputStream)
        }
}
