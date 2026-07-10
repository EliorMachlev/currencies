package de.salomax.currencies.util

private const val SIGNIFICANT_THRESHOLD = 0.01f

fun calculateDifference(old: Float?, new: Float?): Float? {
    return if (old == null || new == null)
        null
    else {
        val percentage = (new - old) / old * 100
        if (percentage.isFinite())
            percentage
        else
            null
    }
}

fun Float.getSignificantDecimalPlaces(significantNumbers: Int = 2): Int {
    if (this >= SIGNIFICANT_THRESHOLD) {
        return significantNumbers
    }
    val decimalStr = this.toBigDecimal().stripTrailingZeros().toPlainString()
    val decimalPart = decimalStr.substringAfter('.', "")
    // find leading zeros
    val leadingZeros = decimalPart.takeWhile { it == '0' }.length
    // take x significant numbers after leading zeros
    val significantDigits = decimalPart.drop(leadingZeros).take(significantNumbers).length
    return leadingZeros + significantDigits
}
