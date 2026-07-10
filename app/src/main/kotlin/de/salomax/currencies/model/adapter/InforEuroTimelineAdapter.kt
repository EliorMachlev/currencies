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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Suppress("unused", "UNUSED_PARAMETER")
internal class InforEuroTimelineAdapter(
    private val startDate: LocalDate,
    private val endDate: LocalDate
) {

    @Synchronized
    @FromJson
    @Throws(IOException::class)
    fun fromJson(reader: JsonReader): Timeline {
        val rates = mutableMapOf<LocalDate, Rate>()
        val datePattern = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) return readErrorResponse(reader)

        reader.beginArray()
        while (reader.hasNext()) {
            processEntry(reader, datePattern, rates)
        }
        reader.endArray()

        return Timeline(
            success = rates.isNotEmpty(),
            error = null,
            base = Currency.EUR.iso4217Alpha(),
            startDate = startDate,
            endDate = endDate,
            rates = rates.toSortedMap(compareBy { it }),
            provider = ApiProvider.INFOR_EURO
        )
    }

    private fun processEntry(
        reader: JsonReader,
        datePattern: DateTimeFormatter,
        rates: MutableMap<LocalDate, Rate>
    ) {
        reader.beginObject()
        var currencyIso: Currency? = null
        var value: BigDecimal? = null
        var dateStart: LocalDate? = null
        var dateEnd: LocalDate? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "currencyIso" -> currencyIso = Currency.fromString(reader.nextString())
                "amount" -> value = BigDecimal(reader.nextString())
                "dateStart" -> dateStart = LocalDate.parse(reader.nextString(), datePattern)
                "dateEnd" -> dateEnd = LocalDate.parse(reader.nextString(), datePattern)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (currencyIso == null || value == null || dateStart == null || dateEnd == null) return
        // inclusive: before-or-equal start
        if (startDate.withDayOfMonth(1).isAfter(dateStart)) return

        var date: LocalDate = dateEnd
        while (!date.isBefore(dateStart)) {
            rates[date] = Rate(currencyIso, value)
            date = date.minusDays(1)
        }
    }

    private fun readErrorResponse(reader: JsonReader): Timeline {
        reader.beginObject()
        var message: String? = null
        while (reader.hasNext()) {
            if (reader.nextName() == "message")
                message = reader.nextString()
        }
        reader.endObject()
        return Timeline(
            success = false,
            error = message,
            base = Currency.EUR.iso4217Alpha(),
            startDate = null,
            endDate = null,
            rates = null,
            provider = ApiProvider.INFOR_EURO
        )
    }

    @Synchronized
    @ToJson
    @Throws(IOException::class)
    fun toJson(writer: JsonWriter, value: Timeline) {
        writer.nullValue()
    }

}
