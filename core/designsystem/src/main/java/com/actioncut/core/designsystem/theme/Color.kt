package com.actioncut.core.designsystem.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * ActionCut brand palette — dark-first, jazzy and energetic: near-black canvases with a
 * vibrant red primary and a warm gold accent (think neon marquee / jazz club), reds and
 * teals for the timeline lanes. No purple.
 */

// Brand accents — vibrant red + warm gold
val Red = Color(0xFFFF2E4D) // primary
val RedDeep = Color(0xFFCC1B36) // primary container / pressed
val Coral = Color(0xFFFF6B3D) // warm secondary accent (gradients)
val Gold = Color(0xFFFFC247) // highlight / secondary
val Pink = Color(0xFFFF5C8A)
val Amber = Color(0xFFFFB02E)

/** Signature warm gradient used for the FAB, hero accents and badges. */
val BrandGradient = Brush.linearGradient(listOf(Red, Coral))
val BrandGradientWide = Brush.linearGradient(listOf(Red, Coral, Gold))

// Dark canvas (neutral near-black with the faintest warm tint)
val Ink900 = Color(0xFF0B0A0B) // app background
val Ink800 = Color(0xFF151215) // surface
val Ink700 = Color(0xFF201B1E) // elevated surface / cards
val Ink600 = Color(0xFF2C2528) // borders / dividers
val Ink500 = Color(0xFF3D343A) // disabled / outline
val Ink300 = Color(0xFF8F8A8E) // secondary text
val Ink100 = Color(0xFFEDE9EC) // primary text on dark

// Light canvas
val Snow = Color(0xFFFFFFFF)
val Cloud = Color(0xFFFBF5F4)
val Mist = Color(0xFFF0E7E8)
val Slate = Color(0xFF6A5A5C)
val Coal = Color(0xFF1A1416)

// Status
val Danger = Color(0xFFFF4D4D)
val Success = Color(0xFF3DDC84)

// Timeline lane tints (CapCut-style colored clip backgrounds) — warm reds + teal, no purple
val LaneVideo = Color(0xFF6E2233) // deep rose-red
val LaneAudio = Color(0xFF0E4D45) // teal (complements red)
val LaneText = Color(0xFF5C3A14) // amber-brown
val LaneOverlay = Color(0xFF8A3A1E) // burnt orange / rust
