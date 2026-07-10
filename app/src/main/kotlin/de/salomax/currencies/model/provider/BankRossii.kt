package de.salomax.currencies.model.provider

import android.content.Context
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.result.Result
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.BankRossiiCurrencyCodesXmlParser
import de.salomax.currencies.model.adapter.BankRossiiRatesXmlParser
import de.salomax.currencies.model.adapter.BankRossiiTimelineXmlParser
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BankRossii : ApiProvider.Api() {

    override val name = "Bank Rossii"

    override fun descriptionShort(context: Context) =
        context.getText(R.string.api_bankRossii_descriptionShort)

    override fun getDescriptionLong(context: Context) =
        context.getText(R.string.api_bankRossii_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) =
        context.getText(R.string.api_bankRossii_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) =
        null

    override val baseUrl = "https://www.cbr.ru/scripts"

    override suspend fun getRates(context: Context?, date: LocalDate?): Result<ExchangeRates, FuelError> {
        val dateString =
            // latest
            if (date == null) ""
            // historical
            else "?date_req=${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"

        return Fuel.get(
            baseUrl +
                    "/XML_daily.asp" +
                    dateString
        ).awaitResult(
            object : ResponseDeserializable<ExchangeRates> {
                override fun deserialize(inputStream: InputStream): ExchangeRates {
                    return BankRossiiRatesXmlParser().parse(inputStream)
                }
            }
        )
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<Timeline, FuelError> {
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        // can't search for FOK - have to use DKK instead
        val parameterBase = if (base == Currency.FOK) "DKK" else base.iso4217Alpha()
        val parameterSymbol = if (symbol == Currency.FOK) "DKK" else symbol.iso4217Alpha()

        val ids = fetchCurrencyIds().get()

        val idBase = if (parameterBase != Currency.RUB.iso4217Alpha())
            ids.entries.find { it.value == parameterBase }?.key else null
        val idSymbol = if (parameterSymbol != Currency.RUB.iso4217Alpha())
            ids.entries.find { it.value == parameterSymbol }?.key else null
        val missingId = when {
            parameterBase != Currency.RUB.iso4217Alpha() && idBase == null -> parameterBase
            parameterSymbol != Currency.RUB.iso4217Alpha() && idSymbol == null -> parameterSymbol
            else -> null
        }
        if (missingId != null)
            return Result.error(FuelError.wrap(Throwable("No currency ID found for: $missingId")))

        val timelineRub = buildRubTimeline(startDate, endDate)
        val baseTimeline = if (parameterBase == Currency.RUB.iso4217Alpha())
            Result.success(timelineRub)
        else
            fetchCurrencyTimeline(startDate, endDate, idBase!!, ids, dateFormatter)

        val symbolTimeline = if (parameterSymbol == Currency.RUB.iso4217Alpha())
            Result.success(timelineRub)
        else
            fetchCurrencyTimeline(startDate, endDate, idSymbol!!, ids, dateFormatter)

        val baseRates: Map<LocalDate, Rate>? = baseTimeline.component1()?.rates
        val symbolRates: Map<LocalDate, Rate>? = symbolTimeline.component1()?.rates

        return if (baseRates == null || symbolRates == null)
            Result.error(FuelError.wrap(Throwable("Timeline data unavailable for base or symbol currency")))
        else
            Result.of {
                symbolTimeline.get().copy(
                    rates = symbolRates
                        .filter { (date, _) -> baseRates[date] != null }
                        .mapValues { (date, rate) ->
                            rate.copy(value = rate.value.divide(baseRates[date]!!.value, MathContext.DECIMAL128))
                        }
                )
            }
    }

    private fun buildRubTimeline(startDate: LocalDate, endDate: LocalDate): Timeline {
        val rubMap = LinkedHashMap<LocalDate, Rate>()
        var currentDate = startDate
        while (currentDate.isBefore(endDate) || currentDate.isEqual(endDate)) {
            rubMap[currentDate] = Rate(Currency.RUB, BigDecimal.ONE)
            currentDate = currentDate.plusDays(1)
        }
        return Timeline(
            success = true,
            error = null,
            base = Currency.RUB.iso4217Alpha(),
            startDate = startDate,
            endDate = endDate,
            rates = rubMap.toSortedMap(),
            provider = ApiProvider.BANK_ROSSII
        )
    }

    private suspend fun fetchCurrencyIds(): Result<Map<String, String>, FuelError> {
        return Fuel.get(baseUrl + "/XML_valFull.asp")
            .awaitResult(object : ResponseDeserializable<Map<String, String>> {
                override fun deserialize(inputStream: InputStream): Map<String, String> =
                    BankRossiiCurrencyCodesXmlParser().parse(inputStream)
            })
    }

    private suspend fun fetchCurrencyTimeline(
        startDate: LocalDate,
        endDate: LocalDate,
        currencyId: String,
        ids: Map<String, String>,
        dateFormatter: DateTimeFormatter
    ): Result<Timeline, FuelError> {
        return Fuel.get(
            baseUrl + "/XML_dynamic.asp" +
                "?date_req1=${startDate.format(dateFormatter)}" +
                "&date_req2=${endDate.format(dateFormatter)}" +
                "&VAL_NM_RQ=$currencyId"
        ).awaitResult(object : ResponseDeserializable<Timeline> {
            override fun deserialize(inputStream: InputStream): Timeline =
                BankRossiiTimelineXmlParser(ids).parse(inputStream)
        })
    }

}
