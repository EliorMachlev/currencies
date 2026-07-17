package de.salomax.currencies.util

import java.math.BigDecimal
import java.math.MathContext

private val SIGNIFICANT_THRESHOLD = BigDecimal("0.01")
private val PERCENT_MULTIPLIER = BigDecimal("100")

fun calculateDifference(old: BigDecimal?, new: BigDecimal?): BigDecimal? {
    return if (old == null || new == null || old.compareTo(BigDecimal.ZERO) == 0)
        null
    else
        (new - old).divide(old, MathContext.DECIMAL128) * PERCENT_MULTIPLIER
}

fun BigDecimal.getSignificantDecimalPlaces(significantNumbers: Int = 2): Int {
    if (this.abs() >= SIGNIFICANT_THRESHOLD) {
        return significantNumbers
    }
    val decimalStr = this.abs().stripTrailingZeros().toPlainString()
    val decimalPart = decimalStr.substringAfter('.', "")
    // find leading zeros
    val leadingZeros = decimalPart.takeWhile { it == '0' }.length
    // take x significant numbers after leading zeros
    val significantDigits = decimalPart.drop(leadingZeros).take(significantNumbers).length
    return leadingZeros + significantDigits
}
