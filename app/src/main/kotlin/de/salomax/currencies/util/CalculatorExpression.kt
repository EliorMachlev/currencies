package de.salomax.currencies.util

import org.mariuszgromada.math.mxparser.Expression

// Calculator operator glyphs shown to the user. Kept as constants so the
// "which operator?" check and the "insert this operator" call agree on the
// exact Unicode codepoint (typographical minus and multiplication signs
// differ from ASCII "-" and "*").
const val OPERATOR_PLUS = "\u002B"      // +
const val OPERATOR_MINUS = "\u2212"     // − (minus sign, not hyphen)
const val OPERATOR_MULTIPLY = "\u00D7"  // ×
const val OPERATOR_DIVIDE = "\u00F7"    // ÷

val OPERATOR_REGEX =
    Regex("[$OPERATOR_PLUS$OPERATOR_MINUS$OPERATOR_MULTIPLY$OPERATOR_DIVIDE]")

private val SMART_PERCENT_REGEX =
    Regex("""(\d+(?:\.\d+)?)([+\-])(\d+(?:\.\d+)?)%""")

/**
 * Evaluate a user-facing calculator expression like `"1 + 2 × 4"` and return
 * the numeric result as a plain string. Understands the display glyphs above,
 * "smart percentage" (`A+B%` → `A+(A*B/100)`), simple percentage (`B%` → `B/100`),
 * and pads a trailing operator so a half-typed expression still evaluates.
 * Returns `"0"` on NaN.
 */
fun String.evaluateCalculatorExpression(): String {
    var s = this
        .replace(" ", "")
        .replace(OPERATOR_MINUS, "-")
        .replace(OPERATOR_MULTIPLY, "*")
        .replace(OPERATOR_DIVIDE, "/")
    // smart percentage: A+B% = A+(A*B/100), A-B% = A-(A*B/100)
    s = s.replace(SMART_PERCENT_REGEX) { m ->
        "${m.groupValues[1]}${m.groupValues[2]}(${m.groupValues[1]}*${m.groupValues[3]}/100)"
    }
    // simple percentage: B% = B/100
    s = s.replace("%", "/100")
    // fill, if last character is an operator
    when (s.trim().last()) {
        '/' -> s += "1"
        '*' -> s += "1"
        '+' -> s += "0"
        '-' -> s += "0"
        '.' -> s += "0"
    }
    val result = Expression(s).calculate()
    return if (result.isNaN()) "0" else result.toBigDecimal().toPlainString()
}
