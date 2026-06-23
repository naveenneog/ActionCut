package com.actioncut.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography scale. Uses the platform default family so the module needs no bundled
 * font assets to build; drop custom fonts into `res/font` and swap [Brand] to enable
 * them (keys are declared in `core:model` `Fonts`).
 */
private val Brand = FontFamily.Default

val ActionCutTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp,
    ),
)
