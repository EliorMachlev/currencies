package de.salomax.currencies.model.adapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

private const val CURRENCY_CODE_START = 2
private const val CURRENCY_CODE_END = 5

@Suppress("unused", "UNUSED_PARAMETER")
internal class BankOfCanadaRatesAdapter {

    @Synchronized
    @FromJson
    @Throws(IOException::class)
    fun fromJson(reader: JsonReader): ExchangeRates? {
        reader.beginObject()
        var result: ExchangeRates? = null
        while (reader.hasNext() && result == null) {
            when (reader.nextName()) {
                "message" -> result = ExchangeRates(
                    success = false,
                    error = reader.nextString(),
                    base = null,
                    date = null,
                    rates = null,
                    provider = ApiProvider.BANK_OF_CANADA
                )
                "observations" -> {
                    reader.beginArray()
                    result = convertObservation(reader)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return result
    }

    private fun convertObservation(reader: JsonReader): ExchangeRates {
        var errorMessage: String? = null
        var date: LocalDate? = null
        val rates = mutableListOf<Rate>()

        if (reader.peek() == JsonReader.Token.END_ARRAY)
            // no data
            errorMessage = "No data found."
        else {
            reader.beginObject()
            while (reader.hasNext()) {
                when (val nextName = reader.nextName()) {
                    // date
                    "d" -> date = LocalDate.parse(reader.nextString())
                    // rate
                    else -> {
                        val currency = Currency.fromString(nextName.substring(CURRENCY_CODE_START, CURRENCY_CODE_END))
                        reader.beginObject()
                        reader.skipName() // always "v"
                        val value = BigDecimal(reader.nextString())
                        reader.endObject()
                        currency?.let { rates.add(Rate(it, BigDecimal.ONE.divide(value, MathContext.DECIMAL128))) }
                    }
                }
            }
        }
        if (rates.isNotEmpty())
            // finally, add CAD...
            rates.add(Rate(Currency.CAD, BigDecimal.ONE))

        return ExchangeRates(
            success = errorMessage == null && rates.isNotEmpty() && date != null,
            error = errorMessage,
            base = Currency.CAD,
            date = date,
            rates = rates,
            provider = ApiProvider.BANK_OF_CANADA
        )
    }

    @Synchronized
    @ToJson
    @Throws(IOException::class)
    fun toJson(writer: JsonWriter, value: ExchangeRates?) {
        writer.nullValue()
    }

}
