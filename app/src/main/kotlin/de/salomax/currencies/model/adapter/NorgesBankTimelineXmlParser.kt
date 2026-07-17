package de.salomax.currencies.model.adapter

import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.model.Timeline
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

class NorgesBankTimelineXmlParser(
    val base: Currency,
    val symbol: Currency,
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    private val ratesList = mutableListOf<Map<LocalDate, Rate>>()

    fun parse(inputStream: InputStream): Timeline {
        val parser = newXmlPullParser(inputStream)

        var eventType = parser.eventType
        var seriesCurrency: Currency? = null
        var multiplier = 1
        val currentTimeline = mutableMapOf<LocalDate, Rate>()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagname = parser.name
            when {
                eventType == XmlPullParser.START_TAG
                    && tagname.equals("Series", ignoreCase = true) -> {
                    seriesCurrency = Currency.fromString(
                        parser.getAttributeValue(null, "BASE_CUR")
                    )
                    multiplier = parser.norgesBankUnitMultiplier()
                }
                eventType == XmlPullParser.START_TAG
                    && tagname.equals("Obs", ignoreCase = true) -> {
                    val date = LocalDate.parse(parser.getAttributeValue(null, "TIME_PERIOD"))
                    val value = parser.getAttributeValue(null, "OBS_VALUE").toBigDecimalOrNull()
                    if (seriesCurrency != null && value != null)
                        currentTimeline[date] = Rate(
                            seriesCurrency,
                            BigDecimal.ONE.divide(value, MathContext.DECIMAL128) * multiplier.toBigDecimal()
                        )
                }
                eventType == XmlPullParser.END_TAG
                    && tagname.equals("Series", ignoreCase = true) -> {
                    ratesList.add(currentTimeline.toMap())
                    currentTimeline.clear()
                }
            }
            eventType = parser.next()
        }

        ratesList.add(buildNokSeries())
        val rates = mergeRates()
        return Timeline(
            success = rates.isNotEmpty(),
            error = null,
            base = base.iso4217Alpha(),
            startDate = rates.entries.firstOrNull()?.key,
            endDate = rates.entries.lastOrNull()?.key,
            rates = rates.toSortedMap(),
            provider = ApiProvider.NORGES_BANK
        )
    }

    private fun buildNokSeries(): Map<LocalDate, Rate> {
        val series = mutableMapOf<LocalDate, Rate>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            series[currentDate] = Rate(Currency.NOK, BigDecimal.ONE)
            currentDate = currentDate.plusDays(1)
        }
        return series
    }

    private fun mergeRates(): MutableMap<LocalDate, Rate> {
        val rates = mutableMapOf<LocalDate, Rate>()
        val baseList = ratesList.find { it.entries.first().value.currency == this.base }
        val symbolList = ratesList.find { it.entries.first().value.currency == this.symbol }
        if (baseList == null || symbolList == null) return rates
        for (entry in baseList) {
            val baseValue = entry.value.value
            val symbolValue = symbolList[entry.key]?.value ?: continue
            rates[entry.key] = Rate(this.symbol, symbolValue.divide(baseValue, MathContext.DECIMAL128))
        }
        return rates
    }

}
