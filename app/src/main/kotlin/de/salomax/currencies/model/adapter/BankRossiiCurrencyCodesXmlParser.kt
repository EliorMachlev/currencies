package de.salomax.currencies.model.adapter

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

class BankRossiiCurrencyCodesXmlParser {
    private val items = mutableMapOf<String, String>()

    fun parse(inputStream: InputStream): MutableMap<String, String> {
        val parser = XmlPullParserFactory.newInstance()
            .apply { isNamespaceAware = false }.newPullParser()
            .apply { setInput(inputStream, null) }

        var tagname: String? = null
        var eventType = parser.eventType
        var id: String? = null
        var iso4217Alpha: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            tagname = parser.name ?: tagname
            when (eventType) {
                XmlPullParser.START_TAG -> if (tagname == "Item")
                    id = parser.getAttributeValue(null, "ID")
                XmlPullParser.TEXT -> if (tagname == "ISO_Char_Code")
                    iso4217Alpha = parser.text
                XmlPullParser.END_TAG -> if (tagname == "Item") {
                    recordItem(id, iso4217Alpha)
                    id = null
                    iso4217Alpha = null
                }
            }
            eventType = parser.next()
        }

        return items
    }

    private fun recordItem(id: String?, iso4217Alpha: String?) {
        if (id != null && iso4217Alpha != null)
            items[id] = iso4217Alpha
    }

}
