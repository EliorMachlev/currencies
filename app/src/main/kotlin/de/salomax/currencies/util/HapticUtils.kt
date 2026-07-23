package de.salomax.currencies.util

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Perform a "keyboard tap" haptic if the user has enabled haptic feedback in
 * settings. Shared between the main calculator screen and the cart screen so
 * both surfaces honour the same preference the same way.
 */
fun View.hapticTap(enabled: Boolean) {
    if (enabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}
