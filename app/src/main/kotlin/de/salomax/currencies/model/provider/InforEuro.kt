package de.salomax.currencies.model.provider

import android.content.Context
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
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
import de.salomax.currencies.model.adapter.InforEuroRatesAdapter
import de.salomax.currencies.model.adapter.InforEuroTimelineAdapter
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.ZoneOffset

class InforEuro : ApiProvider.Api() {

    override val name = "InforEuro"

    override fun descriptionShort(context: Context) =
        context.getText(R.string.api_inforEuro_descriptionShort)

    override fun getDescriptionLong(context: Context) =
        context.getText(R.string.api_inforEuro_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) =
        context.getText(R.string.api_inforEuro_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) =
        context.getText(R.string.api_inforEuro_hint)

    override val baseUrl = "https://ec.europa.eu/budg/inforeuro/api/public"

    override suspend fun getRates(context: Context?, date: LocalDate?): Result<ExchangeRates, FuelError> {
        return Fuel.get(
            baseUrl +
                    "/monthly-rates" +
                    if (date != null) "?year=${date.year}" + "&month=${date.monthValue}"
                    else ""
        ).awaitResult(
            moshiDeserializerOf(
                Moshi.Builder()
                    .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
                    .apply {
                        add(InforEuroRatesAdapter(date ?: LocalDate.now(ZoneOffset.UTC)))
                    }
                    .build()
                    .adapter(ExchangeRates::class.java)
            )
        ).map { rates ->
            rates.copy(provider = ApiProvider.INFOR_EURO)
        }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<Timeline, FuelError> {
        val parameterBase = base.apiCodeOrDkkForFok()
        val parameterSymbol = symbol.apiCodeOrDkkForFok()

        // InforEuro needs 2 calls: the API only provides EUR <-> symbol, without changing the base.
        // So, we make 2 calls: EUR <-> base & EUR <-> symbol

        val deserializer = moshiDeserializerOf(
            Moshi.Builder()
                .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
                .add(InforEuroTimelineAdapter(startDate, endDate))
                .build()
                .adapter(Timeline::class.java)
        )
        // EUR <-> base
        val resultBase = Fuel.get(
            "$baseUrl/currencies/$parameterBase"
        ).awaitResult(deserializer)
        // EUR <-> symbol
        val resultSymbol = Fuel.get(
            "$baseUrl/currencies/$parameterSymbol"
        ).awaitResult(deserializer)

        return if (resultBase.component2() != null) {
            resultBase
        } else if (resultSymbol.component2() != null) {
            resultSymbol
        } else {
            val timeline = resultBase.get().copy(
                provider = ApiProvider.INFOR_EURO,
                base = (if (base == Currency.FOK) Currency.FOK else base).iso4217Alpha(),
                rates = resultSymbol.get().rates?.map { symbolEntry ->
                    val baseValue = resultBase.get().rates?.get(symbolEntry.key)
                    Pair(
                        symbolEntry.key,
                        Rate(
                            symbol,
                            symbolEntry.value.value.divide(
                                baseValue?.value ?: BigDecimal.ONE,
                                MathContext.DECIMAL128
                            )
                        )
                    )
                }?.toMap()
            )
            Result.of { timeline }
        }

    }

}
