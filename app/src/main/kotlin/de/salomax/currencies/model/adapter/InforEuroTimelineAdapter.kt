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

@Suppress("unused", "UNUSED_PARAMETER")
internal class InforEuroTimelineAdapter(
    private val startDate: LocalDate,
    private val endDate: LocalDate
) {

    private val base: String = Currency.EUR.iso4217Alpha()

    @Synchronized
    @FromJson
    @Throws(IOException::class)
    fun fromJson(reader: JsonReader): Timeline = reader.readArrayOrError(
        onError = ::errorResponse
    ) { r ->
        val rates = buildMap {
            while (r.hasNext()) {
                processEntry(r, this)
            }
        }
        Timeline(
            success = rates.isNotEmpty(),
            error = null,
            base = base,
            startDate = startDate,
            endDate = endDate,
            rates = rates.toSortedMap(compareBy { it }),
            provider = ApiProvider.INFOR_EURO
        )
    }

    private fun processEntry(
        reader: JsonReader,
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
                "dateStart" -> dateStart = LocalDate.parse(reader.nextString(), INFOR_EURO_DATE_FORMATTER)
                "dateEnd" -> dateEnd = LocalDate.parse(reader.nextString(), INFOR_EURO_DATE_FORMATTER)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (currencyIso == null || value == null || dateStart == null || dateEnd == null) return
        // inclusive: before-or-equal start
        if (startDate.withDayOfMonth(1).isAfter(dateStart)) return

        val rate = Rate(currencyIso, value)
        var date: LocalDate = dateEnd
        while (!date.isBefore(dateStart)) {
            rates[date] = rate
            date = date.minusDays(1)
        }
    }

    private fun errorResponse(message: String?): Timeline = Timeline(
        success = false,
        error = message,
        base = base,
        startDate = null,
        endDate = null,
        rates = null,
        provider = ApiProvider.INFOR_EURO
    )

    @Synchronized
    @ToJson
    @Throws(IOException::class)
    fun toJson(writer: JsonWriter, value: Timeline) {
        writer.nullValue()
    }

}
