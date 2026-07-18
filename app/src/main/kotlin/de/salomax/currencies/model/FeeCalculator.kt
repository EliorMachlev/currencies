package de.salomax.currencies.model

import java.math.BigDecimal
import java.math.MathContext

private val PERCENTAGE_DIVISOR = BigDecimal("100")

/**
 * Pure fee-stacking math. Extracted from MainViewModel so it can be exercised
 * without an Android [android.app.Application] context.
 */
object FeeCalculator {

    /**
     * Return only those fees that apply for the given base/destination pair.
     * Global fees always apply; pair-specific fees match by ISO code, optionally
     * in both directions.
     */
    fun applicableFees(
        all: List<Fee>,
        base: Currency?,
        dest: Currency?,
    ): List<Fee> {
        val baseCode = base?.iso4217Alpha()
        val destCode = dest?.iso4217Alpha()
        return all.filter { fee ->
            when (fee) {
                is Fee.GlobalExchange, is Fee.GlobalBank -> true
                is Fee.SpecificPair -> {
                    if (baseCode == null || destCode == null) false
                    else if (fee.from == baseCode && fee.to == destCode) true
                    else fee.bothWays && fee.from == destCode && fee.to == baseCode
                }
            }
        }
    }

    /**
     * Multiplicative stack factor for a subset of fees:
     * `product over subset of (1 +/- percent/100)`.
     */
    fun stackFactor(subset: List<Fee>): BigDecimal {
        var acc = BigDecimal.ONE
        subset.forEach { fee ->
            val delta = fee.percent.divide(PERCENTAGE_DIVISOR, MathContext.DECIMAL128)
            val factor = if (fee.isMarkup) BigDecimal.ONE + delta else BigDecimal.ONE - delta
            acc = acc.multiply(factor, MathContext.DECIMAL128)
        }
        return acc
    }

    /**
     * Combined multiplicative fee factor for the given base/destination pair,
     * computed as `specific * globalExchange * globalBank`.
     */
    fun totalStack(
        all: List<Fee>,
        base: Currency?,
        dest: Currency?,
    ): BigDecimal {
        val applicable = applicableFees(all, base, dest)
        val specific = stackFactor(applicable.filterIsInstance<Fee.SpecificPair>())
        val exchange = stackFactor(applicable.filterIsInstance<Fee.GlobalExchange>())
        val bank = stackFactor(applicable.filterIsInstance<Fee.GlobalBank>())
        return specific.multiply(exchange, MathContext.DECIMAL128)
            .multiply(bank, MathContext.DECIMAL128)
    }
}
