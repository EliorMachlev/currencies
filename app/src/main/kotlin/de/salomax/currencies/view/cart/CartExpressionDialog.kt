package de.salomax.currencies.view.cart

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import de.salomax.currencies.R
import de.salomax.currencies.util.OPERATOR_REGEX
import de.salomax.currencies.viewmodel.main.CalculatorInputState

/**
 * Modal calculator that mirrors the main screen's keypad. Instead of a
 * system IME, cart rows delegate expression editing here so the user gets
 * the same digit/operator layout everywhere.
 *
 * The dialog owns a [CalculatorInputState] pre-seeded with [initial]; the
 * hosting [CartActivity] forwards its keypad on-click reflection targets to
 * that state via [CartActivity.activeCalculatorState].
 */
object CartExpressionDialog {

    fun show(
        activity: AppCompatActivity,
        initial: String,
        onResult: (String) -> Unit,
    ) {
        val state = CalculatorInputState().apply { seed(initial) }
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_cart_expression, null, false)
        val baseText = view.findViewById<TextView>(R.id.cart_expr_base)
        val calcText = view.findViewById<TextView>(R.id.cart_expr_calc)

        val baseObserver = Observer<String?> { baseText.text = it }
        val calcObserver = Observer<String?> {
            calcText.text = it.orEmpty()
            calcText.visibility = if (it.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        state.baseValueText.observeForever(baseObserver)
        state.calculationValueText.observeForever(calcObserver)

        // Route the keypad's XML-declared reflection targets (numberEvent /
        // calculationEvent / …) to this dialog's state for the duration of
        // the dialog. Reset on dismiss so a lingering listener doesn't grow
        // stale state after the user cancels.
        val host = activity as? CartActivity
        host?.activeCalculatorState = state

        AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onResult(state.toExpressionString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                host?.activeCalculatorState = null
                state.baseValueText.removeObserver(baseObserver)
                state.calculationValueText.removeObserver(calcObserver)
            }
            .show()
    }
}

/**
 * Rebuild the input state from a previously-saved cart-row expression so the
 * dialog opens where the user left off (rather than "0"). Values that fail
 * to parse fall back to the default "0" seed.
 */
private fun CalculatorInputState.seed(expression: String) {
    val trimmed = expression.trim()
    if (trimmed.isEmpty()) return
    // Calculation-mode strings already contain an operator; base-mode values
    // are just a number.
    if (trimmed.contains(OPERATOR_REGEX)) {
        replayTokens(trimmed)
    } else {
        replayDigits(trimmed)
    }
}

/**
 * Replay a single number back through the input state's digit/decimal API so
 * we don't have to reach into its private LiveData.
 */
private fun CalculatorInputState.replayDigits(number: String) {
    number.forEach { ch ->
        when {
            ch.isDigit() -> addNumber(ch.toString())
            ch == '.' -> addDecimal()
        }
    }
}

private fun CalculatorInputState.replayTokens(expression: String) {
    // Split on operator boundaries while keeping the operators themselves as
    // separate tokens — mirrors how the state serialises them back out.
    val tokens = mutableListOf<String>()
    val buf = StringBuilder()
    expression.trim().forEach { ch ->
        if (ch.toString().matches(OPERATOR_REGEX)) {
            if (buf.isNotBlank()) tokens += buf.toString().trim()
            tokens += ch.toString()
            buf.clear()
        } else {
            buf.append(ch)
        }
    }
    if (buf.isNotBlank()) tokens += buf.toString().trim()

    // The digit-replay API is state-aware: it targets the base row before
    // the first operator and the calculation row afterwards, so we can use
    // it for every operand without a special first-operand branch.
    tokens.forEach { token ->
        if (token.matches(OPERATOR_REGEX)) addOperator(token) else replayDigits(token)
    }
}

/**
 * Serialise the current input state back to a string the cart layer can
 * store and later evaluate.
 */
private fun CalculatorInputState.toExpressionString(): String {
    val calc = calculationValueText.value
    return if (calc.isNullOrBlank()) baseValueText.value.orEmpty() else calc.trim()
}
