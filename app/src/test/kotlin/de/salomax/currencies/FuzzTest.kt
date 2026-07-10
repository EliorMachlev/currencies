package de.salomax.currencies

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import de.salomax.currencies.model.Currency
import de.salomax.currencies.util.toNumber

class FuzzTest {

    @FuzzTest
    fun fuzzCurrencyFromString(data: FuzzedDataProvider) {
        val code = data.consumeRemainingAsString()
        Currency.fromString(code)
    }

    @FuzzTest
    fun fuzzToNumber(data: FuzzedDataProvider) {
        val input = data.consumeRemainingAsString()
        input.toNumber()
    }
}
