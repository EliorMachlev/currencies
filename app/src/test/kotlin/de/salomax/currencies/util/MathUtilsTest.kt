package de.salomax.currencies.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class MathUtilsTest {

    @Test
    fun calculateDifferenceTest() {
        assertEquals(0, calculateDifference(bd("100"), bd("110"))?.compareTo(bd("10")))
        assertEquals(0, calculateDifference(bd("100"), bd("90"))?.compareTo(bd("-10")))
        assertNull(calculateDifference(null, bd("1")))
        assertNull(calculateDifference(bd("1"), null))
        assertNull(calculateDifference(null, null))
        assertNull(calculateDifference(BigDecimal.ZERO, bd("1")))
    }

    @Test
    fun getSignificantDecimalPlacesTest() {
        assertEquals(2, bd("1").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.9").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.991").getSignificantDecimalPlaces(2))
        assertEquals(4, bd("0.0026").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.998").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.9991").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.99991").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.999991").getSignificantDecimalPlaces(2))
        assertEquals(5, bd("0.9999991").getSignificantDecimalPlaces(5))
    }

    private fun bd(value: String) = BigDecimal(value)

}
