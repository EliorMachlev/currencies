package de.salomax.currencies.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import de.salomax.currencies.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.*

private const val SYMBOL_DETECTION_VALUE = 1.23
private const val THOUSANDS_GROUP_SIZE = 3

private const val LTR_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

// Unicode RTL mark (U+200F) injected by some locales' number/date formatters.
// Stripped before rendering side-by-side "left = right" strings so the equals
// sign doesn't get pushed to the opposite side of the display.
private const val RTL_MARK = "\u200F"

// User-configurable decimal places for displayed conversion results.
// Kept in one place so PreferenceFragment (writer) and the spinner list
// (reader) can't drift on the allowed range or the fallback default.
internal const val DECIMAL_PLACES_DEFAULT = 2
internal const val DECIMAL_PLACES_MIN = 0
internal const val DECIMAL_PLACES_MAX = 6

/**
 * Wrap [s] in Unicode LTR isolate (U+2066 … U+2069) so an "<amount> <currency>"
 * chunk stays glued together as one LTR unit inside an RTL paragraph — otherwise
 * the neutral space between number and currency gets absorbed by the RTL run and
 * the currency code drifts to the opposite side.
 */
fun ltrIsolate(s: String): String = "$LTR_ISOLATE$s$POP_DIRECTIONAL_ISOLATE"

/**
 * Removes any Unicode RTL mark (U+200F) from the string.
 */
fun String.stripRtlMark(): String = replace(RTL_MARK, "")

/**
 * Return the *used* Locale, based on the currently active resource folder,
 * not the one set in the System (which one would get with context.resources.configuration.locales[0]).
 * Example: app is localized in en (default) + fr
 * - system=en & app=system-default -> en
 * - system=fr & app=system-default -> fr
 * - system=en & app=en             -> en
 * - system=en & app=fr             -> fr
 * - system=af & app=system-default -> en (as there's no af localization it falls back to en)
 */
fun getLocale(context: Context): Locale {
    return AppCompatDelegate.getApplicationLocales()[0] ?: Locale.of(
        context.getString(R.string.locale_language),
        context.getString(R.string.locale_country)
    )
}

/**
 * Returns the DecimalFormatSymbols for the localization that is active in the app.
 */
private fun getDecimalSymbols(context: Context): DecimalFormatSymbols {
    val decimalFormatter = NumberFormat.getInstance(getLocale(context)) as DecimalFormat
    return decimalFormatter.decimalFormatSymbols
}

/**
 * Returns the decimal separator character for the localization that is active in the app.
 */
fun getDecimalSeparator(context: Context): String {
    return getDecimalSymbols(context).decimalSeparator.toString()
}

/**
 * Returns the grouping separator character for the localization that is active in the app.
 */
fun getGroupingSeparator(context: Context): String {
    return getDecimalSymbols(context).groupingSeparator.toString()
}

/**
 * True, when the currency symbol should be placed after the value for the current locale.
 * False, when the currency symbol should be placed before the value.
 */
fun hasAppendedCurrencySymbol(context: Context): Boolean {
    val currencyFormatter = NumberFormat.getCurrencyInstance(getLocale(context))
    val formattedCurrency = currencyFormatter.format(SYMBOL_DETECTION_VALUE)
    return formattedCurrency.last().digitToIntOrNull() == null
}

// *************************************************************************************************

/**
 * Changes "12345678.12" to "12 345 678.12"
 * - adds thousands separators: groups number blocks
 * - uses the decimal separator of the locale
 * - uses the correct numbers (e.g. east arabian) of the locale
 * - adds plus sign and/or a suffix, if wanted
 */
fun BigDecimal.toHumanReadableNumber(
    context: Context,
    decimalPlaces: Int? = null,
    showPositiveSign: Boolean = false,
    suffix: String? = null,
    trim: Boolean = false
): String {
    return this
        .toPlainString()
        .toHumanReadableNumber(context, decimalPlaces, showPositiveSign, suffix, trim)
}

/**
 * Changes "12345678.12" to "12 345 678.12"
 * - adds thousands separators: groups number blocks
 * - uses the decimal separator of the locale
 * - adds plus sign and/or a suffix, if wanted
 */
fun String.toHumanReadableNumber(
    context: Context,
    decimalPlaces: Int? = null,
    showPositiveSign: Boolean = false,
    suffix: String? = null,
    trim: Boolean = false
): String {
    val formatted = this
        .let { roundIfNeeded(it, decimalPlaces) }
        .let { if (trim) trimTrailingZeros(it) else it }
        .let { applyGrouping(it, context) }
        .replace("-", "- ")
    return buildString {
        if (showPositiveSign && isNonNegative(this@toHumanReadableNumber))
            append("+ ")
        append(formatted)
        if (suffix != null) append(" $suffix")
    }
}

private fun roundIfNeeded(value: String, decimalPlaces: Int?): String {
    if (decimalPlaces == null) return value
    // also converts scientific to natural (123456789.123 instead of 1.23456789E8)
    return value.toBigDecimal()
        .setScale(decimalPlaces, RoundingMode.HALF_EVEN)
        .toPlainString()
}

private fun trimTrailingZeros(value: String): String =
    value.replace("(?!^)\\.?0+$".toRegex(), "")

private fun applyGrouping(value: String, context: Context): String {
    if (!value.contains('.')) return value.groupNumbers(context)
    val (intPart, fracPart) = value.split('.', limit = 2).let { it[0] to it[1] }
    return intPart.groupNumbers(context) + getDecimalSeparator(context) + fracPart
}

private fun isNonNegative(raw: String): Boolean {
    val parsed = DecimalFormat.getInstance().parse(raw) ?: return false
    return parsed.toDouble() >= 0.0
}

private fun String.groupNumbers(context: Context): String {
    val separator = getGroupingSeparator(context)
    val sb = StringBuilder(this.length * 2)
    for ((i, c) in this.reversed().withIndex()) {
        if (i % THOUSANDS_GROUP_SIZE == 0 && i != 0)
            sb.append(separator)
        sb.append(c)
    }
    return sb.toString().reversed()
        .replace("-$separator", "-")
}

// *************************************************************************************************

/**
 * Parses the given string to a number. Uses the default locale for thousands and decimal separators.
 * - Returns null, if invalid characters are found.
 * - Also returns null, for negative values
 */
fun CharSequence.toNumber(): Number? {
    // allow 0-9 , . whitespace only
    if (isBlank() || !matches("[0-9,.\\s]+".toRegex())) return null
    return NumberFormat.getNumberInstance().parse(toString().replace("\\s+".toRegex(), ""))
}
