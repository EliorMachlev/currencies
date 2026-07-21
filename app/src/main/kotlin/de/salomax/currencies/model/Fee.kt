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
    abstract val type: FeeType

    /** Applies to every conversion, no matter which currencies are involved. */
    data class GlobalExchange(
        override val id: String,
        override val percent: BigDecimal,
        override val isMarkup: Boolean,
    ) : Fee() {
        override val type: FeeType get() = FeeType.GLOBAL_EXCHANGE
    }

    /** Bank / card fee that also applies to every conversion. */
    data class GlobalBank(
        override val id: String,
        override val percent: BigDecimal,
        override val isMarkup: Boolean,
    ) : Fee() {
        override val type: FeeType get() = FeeType.GLOBAL_BANK
    }

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
    ) : Fee() {
        override val type: FeeType get() = FeeType.SPECIFIC_PAIR
    }
}

/**
 * Discriminator tag for the [Fee] sealed hierarchy, kept next to the model so
 * on-disk backups and the [Database] JSON encoding read from a single source
 * of truth. `wire` values are persisted verbatim and **must not change**.
 */
enum class FeeType(val wire: String) {
    GLOBAL_EXCHANGE("global_exchange"),
    GLOBAL_BANK("global_bank"),
    SPECIFIC_PAIR("specific_pair");

    companion object {
        fun fromWire(wire: String?): FeeType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Which side of the conversion the fee applies to.
 * - [ORIGINAL]: the displayed converted amount is the mid-market value; the
 *   fee only informs an additional "true cost" indicator on the input side.
 * - [CONVERTED]: the fee is baked into the displayed converted amount.
 */
enum class FeeSide {
    ORIGINAL,
    CONVERTED;

    fun toggled(): FeeSide = when (this) {
        ORIGINAL -> CONVERTED
        CONVERTED -> ORIGINAL
    }
}
