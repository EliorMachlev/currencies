package de.salomax.currencies.viewmodel.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import de.salomax.currencies.util.OPERATOR_DIVIDE
import de.salomax.currencies.util.OPERATOR_MINUS
import de.salomax.currencies.util.OPERATOR_MULTIPLY
import de.salomax.currencies.util.OPERATOR_PLUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalculatorInputStateTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private fun state() = CalculatorInputState()

    private val CalculatorInputState.base: String? get() = baseValueText.value
    private val CalculatorInputState.calc: String? get() = calculationValueText.value

    @Test
    fun `initial state is zero and not in calculation mode`() {
        val s = state()
        assertEquals("0", s.base)
        assertNull(s.calc)
        assertFalse(s.isInCalculationMode())
    }

    @Test
    fun `digits replace initial zero on base row`() {
        val s = state()
        s.addNumber("5")
        assertEquals("5", s.base)
        s.addNumber("3")
        assertEquals("53", s.base)
    }

    @Test
    fun `double or triple zero is collapsed when base is zero`() {
        val s = state()
        s.addNumber("00")
        assertEquals("0", s.base)
        s.addNumber("000")
        assertEquals("0", s.base)
    }

    @Test
    fun `decimal point sticks once on base row`() {
        val s = state()
        s.addNumber("1")
        s.addDecimal()
        s.addDecimal()
        s.addNumber("5")
        assertEquals("1.5", s.base)
    }

    @Test
    fun `operator switches to calculation mode seeded from base`() {
        val s = state()
        s.addNumber("4")
        s.addOperator(OPERATOR_PLUS)
        assertTrue(s.isInCalculationMode())
        assertEquals("4 $OPERATOR_PLUS ", s.calc)
    }

    @Test
    fun `swap trailing operator`() {
        val s = state()
        s.addNumber("4")
        s.addOperator(OPERATOR_PLUS)
        s.addOperator(OPERATOR_MULTIPLY)
        assertEquals("4 $OPERATOR_MULTIPLY ", s.calc)
    }

    @Test
    fun `full expression composition`() {
        val s = state()
        s.addNumber("4")
        s.addOperator(OPERATOR_PLUS)
        s.addNumber("2")
        s.addOperator(OPERATOR_MINUS)
        s.addNumber("1")
        assertEquals("4 $OPERATOR_PLUS 2 $OPERATOR_MINUS 1", s.calc)
    }

    @Test
    fun `delete on base row drops one char and collapses to zero`() {
        val s = state()
        s.addNumber("1")
        s.addNumber("2")
        s.delete()
        assertEquals("1", s.base)
        s.delete()
        assertEquals("0", s.base)
    }

    @Test
    fun `delete in calc mode drops back to base when only single number remains`() {
        val s = state()
        s.addNumber("4")
        s.addOperator(OPERATOR_PLUS)
        s.addNumber("2")
        // deleting the '2' leaves "4 + " which still contains an operator;
        // deleting again removes the operator so we drop out of calc mode.
        s.delete()
        s.delete()
        assertNull(s.calc)
        assertFalse(s.isInCalculationMode())
    }

    @Test
    fun `clear resets both rows`() {
        val s = state()
        s.addNumber("9")
        s.addOperator(OPERATOR_PLUS)
        s.addNumber("1")
        s.clear()
        assertEquals("0", s.base)
        assertNull(s.calc)
    }

    @Test
    fun `paste replaces base row`() {
        val s = state()
        s.addNumber("5")
        s.paste(123)
        assertEquals("123", s.base)
    }

    @Test
    fun `addPercent copies base into calc row when not in calc mode`() {
        val s = state()
        s.addNumber("5")
        s.addNumber("0")
        s.addPercent()
        assertEquals("50%", s.calc)
    }

    @Test
    fun `addDecimal after operator inserts leading zero`() {
        val s = state()
        s.addNumber("4")
        s.addOperator(OPERATOR_PLUS)
        s.addDecimal()
        assertEquals("4 $OPERATOR_PLUS 0.", s.calc)
    }

    @Test
    fun `operator after trailing decimal in calc mode drops the decimal`() {
        // Only applies once we are already in calc mode — the cleanup is on the
        // upper row. From base row, "4." carries forward unmodified.
        val s = state()
        s.addNumber("4")
        s.addOperator(OPERATOR_PLUS)   // calc = "4 + "
        s.addNumber("2")               // calc = "4 + 2"
        s.addDecimal()                 // calc = "4 + 2."
        s.addOperator(OPERATOR_DIVIDE) // calc = "4 + 2 ÷ "
        assertEquals("4 $OPERATOR_PLUS 2 $OPERATOR_DIVIDE ", s.calc)
    }

    @Test
    fun `double zero after operator becomes single zero`() {
        val s = state()
        s.addNumber("4")
        s.addOperator(OPERATOR_PLUS)
        s.addNumber("00")
        assertEquals("4 $OPERATOR_PLUS 0", s.calc)
    }
}
