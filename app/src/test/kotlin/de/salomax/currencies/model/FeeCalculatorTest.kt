package de.salomax.currencies.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

class FeeCalculatorTest {

    private fun bd(s: String) = BigDecimal(s)

    private fun globalExchange(id: String, percent: String, markup: Boolean = true) =
        Fee.GlobalExchange(id = id, percent = bd(percent), isMarkup = markup)

    private fun globalBank(id: String, percent: String, markup: Boolean = true) =
        Fee.GlobalBank(id = id, percent = bd(percent), isMarkup = markup)

    private fun pair(
        id: String,
        percent: String,
        from: String,
        to: String,
        bothWays: Boolean = false,
        markup: Boolean = true,
    ) = Fee.SpecificPair(
        id = id,
        percent = bd(percent),
        isMarkup = markup,
        from = from,
        to = to,
        bothWays = bothWays,
    )

    private fun near(expected: String, actual: BigDecimal, tolerance: String = "0.000001") {
        val diff = (bd(expected) - actual).abs()
        assertTrue(
            "expected ~$expected but got $actual",
            diff <= bd(tolerance),
        )
    }

    @Test
    fun `empty fee list returns identity stack`() {
        val stack = FeeCalculator.totalStack(emptyList(), Currency.USD, Currency.EUR)
        assertEquals(0, stack.compareTo(BigDecimal.ONE))
    }

    @Test
    fun `global exchange markup applies to any pair`() {
        val fees = listOf(globalExchange("g", "2"))
        val stack = FeeCalculator.totalStack(fees, Currency.USD, Currency.EUR)
        near("1.02", stack)
    }

    @Test
    fun `global fees multiply together`() {
        val fees = listOf(globalExchange("g", "2"), globalBank("b", "1"))
        val stack = FeeCalculator.totalStack(fees, Currency.USD, Currency.EUR)
        // 1.02 * 1.01 = 1.0302
        near("1.0302", stack)
    }

    @Test
    fun `discount (isMarkup=false) subtracts`() {
        val fees = listOf(globalExchange("g", "5", markup = false))
        val stack = FeeCalculator.totalStack(fees, Currency.USD, Currency.EUR)
        near("0.95", stack)
    }

    @Test
    fun `specific pair matches only its direction by default`() {
        val fees = listOf(pair("p", "3", from = "USD", to = "EUR"))
        near("1.03", FeeCalculator.totalStack(fees, Currency.USD, Currency.EUR))
        // reverse: no match, back to identity
        assertEquals(0, FeeCalculator.totalStack(fees, Currency.EUR, Currency.USD)
            .compareTo(BigDecimal.ONE))
    }

    @Test
    fun `specific pair with bothWays matches reverse direction`() {
        val fees = listOf(pair("p", "3", from = "USD", to = "EUR", bothWays = true))
        near("1.03", FeeCalculator.totalStack(fees, Currency.EUR, Currency.USD))
    }

    @Test
    fun `null currency yields no specific-pair matches`() {
        val fees = listOf(pair("p", "3", from = "USD", to = "EUR"))
        assertEquals(0, FeeCalculator.totalStack(fees, null, Currency.EUR)
            .compareTo(BigDecimal.ONE))
        assertEquals(0, FeeCalculator.totalStack(fees, Currency.USD, null)
            .compareTo(BigDecimal.ONE))
    }

    @Test
    fun `null currency does not suppress global fees`() {
        val fees = listOf(globalExchange("g", "2"))
        near("1.02", FeeCalculator.totalStack(fees, null, null))
    }

    @Test
    fun `stackFactor is commutative product of factors`() {
        val a = listOf(globalExchange("a", "2"), globalExchange("b", "3"))
        val b = listOf(globalExchange("b", "3"), globalExchange("a", "2"))
        assertEquals(
            0,
            FeeCalculator.stackFactor(a).compareTo(FeeCalculator.stackFactor(b)),
        )
    }

    @Test
    fun `applicableFees filters specific pairs but keeps globals`() {
        val fees = listOf(
            globalExchange("g", "2"),
            pair("p1", "1", from = "USD", to = "EUR"),
            pair("p2", "1", from = "GBP", to = "JPY"),
        )
        val applicable = FeeCalculator.applicableFees(fees, Currency.USD, Currency.EUR)
        assertEquals(2, applicable.size)
        assertTrue(applicable.any { it.id == "g" })
        assertTrue(applicable.any { it.id == "p1" })
    }

    @Test
    fun `high precision preserved via DECIMAL128`() {
        val fees = listOf(globalExchange("g", "0.1"))
        val stack = FeeCalculator.totalStack(fees, Currency.USD, Currency.EUR)
        // 1 + 0.1/100 = 1.001 exactly
        assertEquals(0, stack.round(MathContext.DECIMAL128).compareTo(bd("1.001")))
    }
}
