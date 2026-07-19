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
import java.time.OffsetDateTime

@Suppress("unused", "UNUSED_PARAMETER")
internal class BankOfIsraelRatesAdapter {

    @Synchronized
    @FromJson
    @Throws(IOException::class)
    fun fromJson(reader: JsonReader): ExchangeRates? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null

        var date: LocalDate? = null
        val rates = mutableListOf<Rate>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "exchangeRates" -> date = readEntries(reader, rates)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (rates.isNotEmpty()) {
            rates.add(Rate(Currency.ILS, BigDecimal.ONE))
            rates.addFokFromDkkIfMissing()
        }

        return ExchangeRates(
            success = rates.isNotEmpty(),
            error = if (rates.isEmpty()) "No data found." else null,
            base = Currency.ILS,
            date = date,
            rates = rates,
            provider = ApiProvider.BANK_OF_ISRAEL,
        )
    }

    private fun readEntries(reader: JsonReader, rates: MutableList<Rate>): LocalDate? {
        var latestDate: LocalDate? = null
        reader.beginArray()
        while (reader.hasNext()) {
            val (rate, obsDate) = readEntry(reader)
            if (rate != null) rates.add(rate)
            if (obsDate != null && (latestDate == null || obsDate.isAfter(latestDate))) {
                latestDate = obsDate
            }
        }
        reader.endArray()
        return latestDate
    }

    private fun readEntry(reader: JsonReader): Pair<Rate?, LocalDate?> {
        var code: String? = null
        var value: BigDecimal? = null
        var unit: BigDecimal = BigDecimal.ONE
        var date: LocalDate? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "key" -> code = reader.nextString()
                "currentExchangeRate" -> value = BigDecimal(reader.nextString())
                "unit" -> unit = BigDecimal(reader.nextString())
                "lastUpdate" -> date = OffsetDateTime.parse(reader.nextString()).toLocalDate()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val currency = code?.let(Currency::fromString)
        val rate = if (currency != null && value != null && value.signum() > 0)
            Rate(currency, unit.divide(value, MathContext.DECIMAL128))
        else null
        return rate to date
    }

    @Synchronized
    @ToJson
    @Throws(IOException::class)
    fun toJson(writer: JsonWriter, value: ExchangeRates?) {
        writer.nullValue()
    }
}
