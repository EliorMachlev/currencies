package de.salomax.currencies.model.adapter

import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.format.DateTimeFormatter
import kotlin.math.pow

// Bank Rossii serializes dates as dd.MM.yyyy in every response.
internal val BANK_ROSSII_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy")

// Faroese króna isn't listed by upstream APIs; mirror the Danish krone
// value into the response so FOK still shows up in the UI as a 1:1 peg.
internal fun MutableList<Rate>.addFokFromDkkIfMissing() {
    if (any { it.currency == Currency.FOK }) return
    find { it.currency == Currency.DKK }?.value?.let { dkk ->
        add(Rate(Currency.FOK, dkk))
    }
}

// Namespace-unaware XmlPullParser bound to [inputStream]. The five XML
// parsers all want this exact configuration.
internal fun newXmlPullParser(inputStream: InputStream): XmlPullParser =
    XmlPullParserFactory.newInstance()
        .apply { isNamespaceAware = false }.newPullParser()
        .apply { setInput(inputStream, null) }

// Norges Bank encodes the currency's decimal scale as UNIT_MULT (an integer
// exponent). Missing/invalid values fall back to 1 (10^0).
internal fun XmlPullParser.norgesBankUnitMultiplier(): Int =
    getAttributeValue(null, "UNIT_MULT")?.toIntOrNull()
        ?.let { 10.0.pow(it).toInt() } ?: 1
