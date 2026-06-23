package com.actioncut.core.common.time

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formatting helpers for converting millisecond durations into human / timeline strings.
 * Kept dependency-free so it can be unit-tested on the JVM.
 */
object TimeFormatter {

    /** `mm:ss` (or `h:mm:ss` when >= 1 hour). Used for the player & clip labels. */
    fun clock(ms: Long): String {
        val totalSeconds = (ms / 1000.0).roundToLong().coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** `mm:ss.t` with tenths — used on the scrubber for fine positioning. */
    fun precise(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        val minutes = safe / 60_000
        val seconds = (safe % 60_000) / 1000
        val tenths = (safe % 1000) / 100
        return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }

    /** `1m 23s` style short duration label for media/project cards. */
    fun short(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }
}
