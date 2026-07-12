package de.salomax.currencies.model.adapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

private const val CURRENCY_CODE_START = 2
private const val CURRENCY_CODE_END = 5

@Suppress("unused", "UNUSED_PARAMETER")
internal class BankOfCanadaTimelineAdapter(
    private val base: Currency,
    private val symbol: Currency
) {

    @Synchronized
    @FromJson
    @Throws(IOException::class)
    fun fromJson(reader: JsonReader): Timeline? {
        reader.beginObject()
        var result: Timeline? = null
        while (reader.hasNext() && result == null) {
            when (reader.nextName()) {
                "message" -> result = Timeline(
                    success = false,
                    error = reader.nextString(),
                    base = null,
                    startDate = null,
                    endDate = null,
                    rates = null,
                    provider = ApiProvider.BANK_OF_CANADA
                )
                "observations" -> {
                    reader.beginArray()
                    result = convertObservations(reader)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return result
    }

    private fun convertObservations(reader: JsonReader): Timeline {
        var errorMessage: String? = null
        var rates = mutableMapOf<LocalDate, Rate>()

        if (reader.peek() == JsonReader.Token.END_ARRAY)
            // no data
            errorMessage = "No data found."
        else {
            while (reader.hasNext() && reader.peek() != JsonReader.Token.END_ARRAY) {
                convertObservation(reader)?.let { rates.put(it.first, it.second) }
            }
        }
        rates = rates.toSortedMap()
        return Timeline(
            success = errorMessage == null && rates.isNotEmpty(),
            error = errorMessage,
            base = base.iso4217Alpha(),
            startDate = rates.entries.first().key,
            endDate = rates.entries.last().key,
            rates = rates,
            provider = ApiProvider.BANK_OF_CANADA
        )
    }

    private fun convertObservation(reader: JsonReader): Pair<LocalDate, Rate>? {
        var date: LocalDate? = null
        var baseValue: BigDecimal? = null
        var symbolValue: BigDecimal? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val nextName = reader.nextName()
            if (nextName == "d") {
                date = LocalDate.parse(reader.nextString())
            } else {
                val (currency, value) = readCurrencyValue(reader, nextName)
                if (currency == base) baseValue = value
                else if (currency == symbol) symbolValue = value
            }
            if (date != null && baseValue != null && symbolValue != null) {
                reader.endObject()
                return Pair(date, Rate(symbol, baseValue.divide(symbolValue, MathContext.DECIMAL128)))
            }
        }
        return null
    }

    private fun readCurrencyValue(reader: JsonReader, name: String): Pair<Currency?, BigDecimal> {
        val currency = Currency.fromString(name.substring(CURRENCY_CODE_START, CURRENCY_CODE_END))
        reader.beginObject()
        reader.skipName() // always "v"
        val value = BigDecimal(reader.nextString())
        reader.endObject()
        return currency to value
    }

    @Synchronized
    @ToJson
    @Throws(IOException::class)
    fun toJson(writer: JsonWriter, value: Timeline?) {
        writer.nullValue()
    }

}
