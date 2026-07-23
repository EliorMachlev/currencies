package de.salomax.currencies.util

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Perform a "keyboard tap" haptic if the user has enabled haptic feedback in
 * settings. Shared between the main calculator screen and the cart screen so
 * both surfaces honour the same preference the same way.
 */
fun View.hapticTap(enabled: Boolean) {
    if (!enabled) return
    // FLAG_IGNORE_VIEW_SETTING: some parent chains disable haptic feedback
    // (e.g. cards, RecyclerView rows), so force the tap regardless of the
    // view-tree setting. The user's global "haptic feedback" system toggle
    // is still respected by the OS.
    performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
    )
}
