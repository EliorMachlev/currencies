package de.salomax.currencies.model

import java.math.BigDecimal

/**
 * A single fee entry that can be stacked with others when converting
 * currencies. All entries store the [percent] as a positive number;
 * whether the fee is added or subtracted is controlled by [isMarkup].
 */
sealed class Fee {
    abstract val id: String
    abstract val percent: BigDecimal
    abstract val isMarkup: Boolean

    /** Applies to every conversion, no matter which currencies are involved. */
    data class GlobalExchange(
        override val id: String,
        override val percent: BigDecimal,
        override val isMarkup: Boolean,
    ) : Fee()

    /** Bank / card fee that also applies to every conversion. */
    data class GlobalBank(
        override val id: String,
        override val percent: BigDecimal,
        override val isMarkup: Boolean,
    ) : Fee()

    /**
     * A fee tied to a specific currency pair. When [bothWays] is true the
     * fee also matches the reverse direction ([to] -> [from]).
     */
    data class SpecificPair(
        override val id: String,
        override val percent: BigDecimal,
        override val isMarkup: Boolean,
        val from: String,
        val to: String,
        val bothWays: Boolean,
    ) : Fee()
}

/**
 * Which side of the conversion the fee applies to.
 * - [ORIGINAL]: the displayed converted amount is the mid-market value; the
 *   fee only informs an additional "true cost" indicator on the input side.
 * - [CONVERTED]: the fee is baked into the displayed converted amount.
 */
enum class FeeSide {
    ORIGINAL,
    CONVERTED,
}
