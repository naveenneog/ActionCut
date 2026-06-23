package com.actioncut.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralised spacing/sizing tokens, exposed via a CompositionLocal so screens read a
 * single source of truth instead of scattering magic numbers.
 */
data class Dimens(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    // Editor-specific
    val toolbarHeight: Dp = 64.dp,
    val timelineTrackHeight: Dp = 56.dp,
    val timelineRulerHeight: Dp = 24.dp,
    val playheadWidth: Dp = 2.dp,
    val clipCorner: Dp = 8.dp,
    val thumbnailSize: Dp = 56.dp,
)

val LocalDimens = staticCompositionLocalOf { Dimens() }
