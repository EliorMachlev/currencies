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
import java.time.LocalDate

@Suppress("unused", "UNUSED_PARAMETER")
internal class InforEuroRatesAdapter(private val date: LocalDate) {

    @Synchronized
    @FromJson
    @Throws(IOException::class)
    fun fromJson(reader: JsonReader): ExchangeRates {
        val rates = mutableListOf<Rate>()

        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) return readErrorResponse(reader)

        reader.beginArray()
        while (reader.hasNext()) {
            parseEntry(reader)?.let { rates.add(it) }
        }
        reader.endArray()

        return ExchangeRates(
            success = rates.isNotEmpty(),
            error = null,
            base = Currency.EUR,
            date = date,
            rates = rates,
            provider = ApiProvider.INFOR_EURO
        )
    }

    private fun parseEntry(reader: JsonReader): Rate? {
        reader.beginObject()
        var name: Currency? = null
        var value: Float? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "isoA3Code" -> name = Currency.fromString(reader.nextString())
                "value" -> value = reader.nextDouble().toFloat()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return if (name != null && value != null) Rate(name, value) else null
    }

    private fun readErrorResponse(reader: JsonReader): ExchangeRates {
        reader.beginObject()
        var message: String? = null
        while (reader.hasNext()) {
            if (reader.nextName() == "message")
                message = reader.nextString()
        }
        reader.endObject()
        return ExchangeRates(
            success = false,
            error = message,
            base = Currency.EUR,
            date = date,
            rates = null,
            provider = ApiProvider.INFOR_EURO
        )
    }

    @Synchronized
    @ToJson
    @Throws(IOException::class)
    fun toJson(writer: JsonWriter, value: ExchangeRates) {
        writer.nullValue()
    }

}
