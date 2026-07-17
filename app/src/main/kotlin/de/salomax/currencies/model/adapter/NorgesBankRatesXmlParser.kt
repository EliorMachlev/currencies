package de.salomax.currencies.model.adapter

import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

class NorgesBankRatesXmlParser {
    private var date: LocalDate? = null
    private val rates = mutableListOf<Rate>()

    fun parse(inputStream: InputStream, requestedDate: LocalDate): ExchangeRates {
        val parser = newXmlPullParser(inputStream)

        var eventType = parser.eventType
        var base: Currency? = null
        var multiplier = 1
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagname = parser.name
            if (eventType == XmlPullParser.START_TAG) {
                when {
                    tagname.equals("Series", ignoreCase = true) -> {
                        base = Currency.fromString(parser.getAttributeValue(null, "BASE_CUR"))
                        multiplier = parser.norgesBankUnitMultiplier()
                    }
                    tagname.equals("Obs", ignoreCase = true) -> {
                        val obsDate = LocalDate.parse(parser.getAttributeValue(null, "TIME_PERIOD"))
                        val value = parser.getAttributeValue(null, "OBS_VALUE").toBigDecimalOrNull()
                        recordObservation(base, value, obsDate, multiplier, requestedDate)
                        base = null
                        updateDate(obsDate)
                    }
                }
            }
            eventType = parser.next()
        }

        addSyntheticRates()
        return ExchangeRates(
            success = rates.isNotEmpty(),
            error = null,
            base = Currency.NOK,
            date = date,
            rates = rates,
            provider = ApiProvider.NORGES_BANK
        )
    }

    private fun recordObservation(
        base: Currency?,
        value: BigDecimal?,
        date: LocalDate,
        multiplier: Int,
        requestedDate: LocalDate
    ) {
        if (base == null || value == null) return
        // api delivers historical rates for e.g. RUB; ignore stale ones
        if (!date.isAfter(requestedDate.minusWeeks(2))) return
        rates.removeIf { rate -> rate.currency == base }
        rates.add(Rate(base, BigDecimal.ONE.divide(value, MathContext.DECIMAL128) * multiplier.toBigDecimal()))
    }

    private fun updateDate(date: LocalDate) {
        if (this.date == null || this.date?.isBefore(date) == true)
            this.date = date
    }

    private fun addSyntheticRates() {
        if (rates.isEmpty()) return
        rates.add(Rate(Currency.NOK, BigDecimal.ONE))
        rates.addFokFromDkkIfMissing()
    }

}
