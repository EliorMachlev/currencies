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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Suppress("unused", "UNUSED_PARAMETER")
internal class OpenExchangeratesRatesAdapter {

    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
    @Synchronized
    @FromJson
    @Throws(IOException::class)
    fun fromJson(reader: JsonReader): ExchangeRates? {
        val rates = mutableListOf<Rate>()
        var base: Currency? = null
        var date: LocalDate? = null
        var errorMessage: String? = null

        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
        reader.beginObject()

        while (reader.hasNext()) {
            if (reader.peek() != JsonReader.Token.NAME) continue
            when (reader.nextName()) {
                "rates" -> rates.addAll(parseRates(reader))
                "timestamp" -> date = Instant.ofEpochSecond(reader.nextLong())
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                "base" -> base = Currency.fromString(reader.nextString())
                "message" -> errorMessage = reader.nextString()
                else -> reader.skipValue()
            }
        }

        reader.endObject()
        addFokIfMissing(rates)

        return if (rates.isNotEmpty())
            ExchangeRates(success = true, error = null, base = base, date = date,
                rates = rates, provider = ApiProvider.OPEN_EXCHANGERATES)
        else
            ExchangeRates(success = false, error = errorMessage, base = base, date = date,
                rates = null, provider = ApiProvider.OPEN_EXCHANGERATES)
    }

    private fun parseRates(reader: JsonReader): List<Rate> {
        val rates = mutableListOf<Rate>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = Currency.fromString(reader.nextName())
            val value: BigDecimal = BigDecimal(reader.nextString())
            if (name != null)
                rates.add(Rate(name, value))
        }
        reader.endObject()
        return rates
    }

    private fun addFokIfMissing(rates: MutableList<Rate>) {
        if (rates.find { it.currency == Currency.FOK } != null) return
        rates.find { it.currency == Currency.DKK }?.value?.let { dkk ->
            rates.add(Rate(Currency.FOK, dkk))
        }
    }

    @Synchronized
    @ToJson
    @Throws(IOException::class)
    fun toJson(writer: JsonWriter, value: ExchangeRates) {
        writer.nullValue()
    }

}
