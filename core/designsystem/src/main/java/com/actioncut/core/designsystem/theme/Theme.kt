package com.actioncut.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = RedDeep,
    onPrimaryContainer = Ink100,
    secondary = Gold,
    onSecondary = Ink900,
    tertiary = Coral,
    onTertiary = Ink900,
    background = Ink900,
    onBackground = Ink100,
    surface = Ink800,
    onSurface = Ink100,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink300,
    outline = Ink500,
    outlineVariant = Ink600,
    error = Danger,
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDE1),
    onPrimaryContainer = Color(0xFF470A14),
    secondary = Color(0xFFB26A00),
    onSecondary = Color.White,
    tertiary = Coral,
    onTertiary = Color.White,
    background = Snow,
    onBackground = Coal,
    surface = Cloud,
    onSurface = Coal,
    surfaceVariant = Mist,
    onSurfaceVariant = Slate,
    outline = Color(0xFFD8C7C9),
    outlineVariant = Mist,
    error = Danger,
    onError = Color.White,
)

/**
 * Root theme for ActionCut. Dark-first: defaults to the dark palette unless the system
 * is in light mode. Optionally honours Material You dynamic color on Android 12+.
 *
 * Also provides [LocalDimens] and configures edge-to-edge system bar icon contrast.
 */
@Composable
fun ActionCutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.background.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalDimens provides Dimens()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ActionCutTypography,
            shapes = ActionCutShapes,
            content = content,
        )
    }
}

/** Convenient accessors so callers can write `ActionCutTheme.dimens`. */
object ActionCutTheme {
    val dimens: Dimens
        @Composable get() = LocalDimens.current
}
