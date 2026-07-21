package de.salomax.currencies.viewmodel.main

/**
 * The four arithmetic operators the calculator keypad exposes. Each entry
 * carries both its on-screen label ([display], as rendered on the keypad
 * button) and the ASCII glyph the hardware keyboard produces ([hardware]),
 * so the same enum can route either input path into the corresponding
 * [MainViewModel] call.
 */
enum class Operator(
    val display: String,
    val hardware: Char,
    val apply: MainViewModel.() -> Unit,
) {
    PLUS  ("+", '+', MainViewModel::addition),
    MINUS ("−", '-', MainViewModel::subtraction),
    TIMES ("×", '*', MainViewModel::multiplication),
    DIVIDE("÷", '/', MainViewModel::division);

    companion object {
        fun fromDisplay(label: String): Operator? = entries.firstOrNull { it.display == label }
        fun fromHardware(char: Char): Operator? = entries.firstOrNull { it.hardware == char }
    }
}
