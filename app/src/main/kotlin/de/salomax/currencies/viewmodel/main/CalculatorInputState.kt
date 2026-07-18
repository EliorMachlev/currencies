package de.salomax.currencies.viewmodel.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.salomax.currencies.util.OPERATOR_REGEX

/**
 * Holds the mutable keypad state — the lower "base" row and the optional upper
 * "calculation" row — and the operations the calculator UI performs on them
 * (digits, decimal point, operators, percent, delete, clear, paste).
 *
 * Extracted from MainViewModel so the string-manipulation edge cases (empty
 * operator slots, trailing decimals, "00"/"000" collapsing, operator swap on
 * a trailing operator) can be exercised without an Android Application.
 *
 * `currentBaseValueText` is never null once initialised; `currentCalculationValueText`
 * is null iff we are not in calculation mode.
 */
internal class CalculatorInputState {

    private val _baseValueText = MutableLiveData("0")
    private val _calculationValueText = MutableLiveData<String?>()

    val baseValueText: LiveData<String?> = _baseValueText
    val calculationValueText: LiveData<String?> = _calculationValueText

    fun isInCalculationMode(): Boolean =
        _calculationValueText.value.isNullOrBlank().not()

    fun addNumber(value: String) {
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
            val lastToken = current.split(" ").last().trim()
            when {
                // last input was "0": replace it with any other number
                lastToken == "0" -> {
                    if (value != "0" && value != "00" && value != "000") {
                        _calculationValueText.value = current.trim().dropLast(1) + value
                    }
                }
                // last input was an operator: collapse "00"/"000" down to "0"
                current.split(" ").last().isEmpty()
                        && (value == "00" || value == "000") -> {
                    _calculationValueText.value = current + "0"
                }
                else -> {
                    _calculationValueText.value = current + value
                }
            }
        } else {
            val current = _baseValueText.value
            _baseValueText.value = if (current == "0") {
                if (value == "00" || value == "000") "0" else value
            } else {
                current + value
            }
        }
    }

    fun paste(value: Number) {
        // clear base value (but not calculation row!)
        _baseValueText.value = "0"
        value.toString().forEach { addNumber(it.toString()) }
    }

    fun addPercent() {
        if (!isInCalculationMode()) {
            _calculationValueText.value = _baseValueText.value
        }
        val current = _calculationValueText.value?.trim() ?: return
        if (current.isNotEmpty() && (current.last().isDigit() || current.last() == '.')) {
            _calculationValueText.value =
                if (current.last() == '.') current.dropLast(1) + "%" else current + "%"
        }
    }

    fun addDecimal() {
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
            if (!current.substringAfterLast(" ").contains(".")) {
                // if last char is not a number: add 0 first
                val prefix = if (!current.trim().last().isDigit()) current + "0" else current
                _calculationValueText.value = "$prefix."
            }
        } else {
            val current = _baseValueText.value!!
            if (!current.contains(".")) {
                _baseValueText.value = "$current."
            }
        }
    }

    fun delete() {
        if (isInCalculationMode()) {
            var next = _calculationValueText.value!!.trim().dropLast(1)
            // if last char is a number: trim any dangling space
            if (next.isNotEmpty() && next.last().isDigit()) next = next.trim()
            // if only a number is left without an operator, drop out of calc mode
            _calculationValueText.value =
                if (!next.contains(OPERATOR_REGEX)) null else next
        } else {
            val current = _baseValueText.value!!
            if (current.length > 1) {
                _baseValueText.value = current.dropLast(1)
            } else {
                clear()
            }
        }
    }

    fun clear() {
        _baseValueText.value = "0"
        _calculationValueText.value = null
    }

    fun addOperator(operator: String) {
        if (isInCalculationMode()) {
            val current = _calculationValueText.value!!
            val lastChar = current.trim().last()
            when {
                // already an operator at the end: swap it
                lastChar.toString().matches(OPERATOR_REGEX) -> {
                    _calculationValueText.value = current.trim().dropLast(1) + "$operator "
                }
                // trailing '.': drop it, then append operator
                lastChar == '.' -> {
                    _calculationValueText.value = current.trim().dropLast(1) + " $operator "
                }
                else -> {
                    _calculationValueText.value = current.trim() + " $operator "
                }
            }
        } else {
            // switch to calculation mode, seeded from the base row
            _calculationValueText.value = _baseValueText.value + " $operator "
        }
    }
}
