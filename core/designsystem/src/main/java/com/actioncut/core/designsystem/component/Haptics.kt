package com.actioncut.core.designsystem.component

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Lightweight haptics controller for editor micro-interactions (Phase 8). Wraps the
 * host [android.view.View] so callers don't touch platform APIs directly.
 */
class HapticController(private val view: android.view.View) {
    /** Subtle tick — e.g. timeline snap, slider detents. */
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** Standard confirm — e.g. button press, clip select. */
    fun click() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    /** Stronger long-press feedback — e.g. drag start. */
    fun longPress() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    /** Success/confirm gesture — e.g. export complete (API 30+, falls back gracefully). */
    fun confirm() = view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
}

@Composable
fun rememberHaptic(): HapticController {
    val view = LocalView.current
    return remember(view) { HapticController(view) }
}
