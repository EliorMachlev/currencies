package de.salomax.currencies.model.adapter

import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Rate
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BankRossiiRatesXmlParser {
    private var date: LocalDate? = null
    private val rates = mutableListOf<Rate>()

    fun parse(inputStream: InputStream): ExchangeRates {
        val parser = XmlPullParserFactory.newInstance()
            .apply { isNamespaceAware = false }.newPullParser()
            .apply { setInput(inputStream, null) }

        var tagname: String? = null
        var eventType = parser.eventType
        var currency: Currency? = null
        var value: BigDecimal? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            tagname = parser.name ?: tagname
            when (eventType) {
                XmlPullParser.START_TAG -> if (tagname == "ValCurs")
                    date = LocalDate.parse(
                        parser.getAttributeValue(null, "Date"),
                        DateTimeFormatter.ofPattern("dd.MM.yyyy")
                    )
                XmlPullParser.TEXT -> when (tagname) {
                    "CharCode" -> currency = Currency.fromString(parser.text)
                    "VunitRate" -> value = BigDecimal.ONE.divide(
                        parser.text.replace(',', '.').toBigDecimal(),
                        MathContext.DECIMAL128
                    )
                }
                XmlPullParser.END_TAG -> if (tagname == "Valute") {
                    recordRate(currency, value)
                    currency = null
                    value = null
                }
            }
            eventType = parser.next()
        }

        addSyntheticRates()
        return ExchangeRates(
            success = rates.isNotEmpty(),
            error = null,
            base = Currency.RUB,
            date = date,
            rates = rates,
            provider = ApiProvider.BANK_ROSSII
        )
    }

    private fun recordRate(currency: Currency?, value: BigDecimal?) {
        if (currency != null && value != null)
            rates.add(Rate(currency, value))
    }

    private fun addSyntheticRates() {
        if (rates.isEmpty()) return
        rates.add(Rate(Currency.RUB, BigDecimal.ONE))
        rates.addFokFromDkkIfMissing()
    }

}
