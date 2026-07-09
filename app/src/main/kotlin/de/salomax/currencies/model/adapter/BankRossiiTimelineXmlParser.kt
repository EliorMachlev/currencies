package de.salomax.currencies.model.adapter

import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BankRossiiTimelineXmlParser(private val ids: Map<String, String>) {
    private val rates = mutableMapOf<LocalDate, Rate>()

    fun parse(inputStream: InputStream): Timeline {
        val parser = XmlPullParserFactory.newInstance()
            .apply { isNamespaceAware = false }.newPullParser()
            .apply { setInput(inputStream, null) }

        var tagname: String? = null
        var eventType = parser.eventType
        var date: LocalDate? = null
        var currencyId: String? = null
        var value: Float? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            tagname = parser.name ?: tagname
            when (eventType) {
                XmlPullParser.START_TAG -> if (tagname == "Record") {
                    date = LocalDate.parse(
                        parser.getAttributeValue(null, "Date"),
                        DateTimeFormatter.ofPattern("dd.MM.yyyy")
                    )
                    currencyId = parser.getAttributeValue(null, "Id")
                }
                XmlPullParser.TEXT -> if (tagname == "VunitRate")
                    value = 1f / parser.text.replace(',', '.').toFloat()
                XmlPullParser.END_TAG -> if (tagname == "Record") {
                    recordRate(date, value, currencyId)
                    date = null
                    currencyId = null
                    value = null
                }
            }
            eventType = parser.next()
        }

        return Timeline(
            success = rates.isNotEmpty(),
            error = null,
            base = rates.values.firstOrNull()?.currency?.iso4217Alpha(),
            startDate = rates.entries.first().key,
            endDate = rates.entries.last().key,
            rates = rates,
            provider = ApiProvider.BANK_ROSSII
        )
    }

    private fun recordRate(date: LocalDate?, value: Float?, currencyId: String?) {
        val isoCode = currencyId?.let { ids[it] } ?: return
        if (date == null || value == null) return
        val currency = Currency.fromString(isoCode) ?: return
        rates[date] = Rate(currency, value)
    }

}
