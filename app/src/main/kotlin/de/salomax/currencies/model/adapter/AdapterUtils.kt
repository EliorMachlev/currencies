package de.salomax.currencies.model.adapter

import com.squareup.moshi.JsonReader
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.format.DateTimeFormatter
import kotlin.math.pow

// Both InforEuro endpoints return a JSON array on success and an
// { "message": "..." } object on failure. Peek at the token: on an array,
// walk it via [onArray] (begin/endArray are handled here); otherwise pass
// the error message to [onError].
internal inline fun <T> JsonReader.readArrayOrError(
    onError: (String?) -> T,
    onArray: (JsonReader) -> T
): T {
    if (peek() != JsonReader.Token.BEGIN_ARRAY) return onError(readErrorMessage())
    beginArray()
    val result = onArray(this)
    endArray()
    return result
}

// Providers surface error payloads as { "message": "..." }. This helper reads
// that message field out of the current object, skipping anything else.
internal fun JsonReader.readErrorMessage(): String? {
    beginObject()
    var message: String? = null
    while (hasNext()) {
        if (nextName() == "message") message = nextString()
        else skipValue()
    }
    endObject()
    return message
}

// Bank Rossii serializes dates as dd.MM.yyyy in every response.
internal val BANK_ROSSII_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy")

// InforEuro's timeline endpoint uses dd/MM/yyyy for dateStart/dateEnd.
internal val INFOR_EURO_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")

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
