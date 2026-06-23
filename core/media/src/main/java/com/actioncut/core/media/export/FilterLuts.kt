package com.actioncut.core.media.export

import android.graphics.Color

/**
 * Generates real 3D colour-lookup cubes for ActionCut's named filters, consumed by
 * Media3's [androidx.media3.effect.SingleColorLut.createFromCube]. This is the actual
 * **LUT render path**: instead of shipping `.cube` asset files, each filter is defined as
 * a per-pixel colour transform that is baked into an N×N×N cube at export time, blended
 * toward identity by the filter's `intensity`.
 *
 * Cube layout matches Media3's contract: `cube[red][green][blue]` holding packed RGB ints.
 */
object FilterLuts {

    /** Cube resolution per channel (17 ≈ smooth gradients with a tiny memory footprint). */
    private const val SIZE = 17

    /** Returns a LUT cube for [filterId], or null if the filter has no colour transform. */
    fun cubeFor(filterId: String, intensity: Float): Array<Array<IntArray>>? {
        val transform = transforms[filterId] ?: return null
        val t = intensity.coerceIn(0f, 1f)
        val max = (SIZE - 1).toFloat()
        return Array(SIZE) { ri ->
            Array(SIZE) { gi ->
                IntArray(SIZE) { bi ->
                    val r = ri / max
                    val g = gi / max
                    val b = bi / max
                    val out = transform(r, g, b)
                    Color.rgb(
                        to255(lerp(r, out[0], t)),
                        to255(lerp(g, out[1], t)),
                        to255(lerp(b, out[2], t)),
                    )
                }
            }
        }
    }

    fun hasLut(filterId: String): Boolean = transforms.containsKey(filterId)

    // Per-filter colour transforms operating on normalized [0,1] RGB.
    private val transforms: Map<String, (Float, Float, Float) -> FloatArray> = mapOf(
        "portrait_soft" to { r, g, b ->
            floatArrayOf(c(r * 0.96f + 0.06f), c(g * 0.97f + 0.05f), c(b * 0.97f + 0.05f))
        },
        "portrait_glow" to { r, g, b ->
            floatArrayOf(c(r * 1.06f + 0.04f), c(g * 1.04f + 0.03f), c(b * 1.03f + 0.03f))
        },
        "food_fresh" to { r, g, b ->
            val l = luma(r, g, b)
            floatArrayOf(c((l + (r - l) * 1.2f) * 1.04f + 0.02f), c(l + (g - l) * 1.2f), c((l + (b - l) * 1.2f) * 0.98f))
        },
        "landscape_vivid" to { r, g, b ->
            val l = luma(r, g, b)
            floatArrayOf(c(l + (r - l) * 1.35f), c(l + (g - l) * 1.35f), c(l + (b - l) * 1.35f))
        },
        "vintage_70s" to { r, g, b ->
            floatArrayOf(c(r * 1.10f + 0.05f), c(g * 1.02f + 0.02f), c(b * 0.85f))
        },
        "vintage_fade" to { r, g, b ->
            val l = luma(r, g, b)
            floatArrayOf(c(lerp(r, l, 0.25f) + 0.07f), c(lerp(g, l, 0.25f) + 0.05f), c(lerp(b, l, 0.25f) + 0.04f))
        },
        "mono_noir" to { r, g, b ->
            val l = c((luma(r, g, b) - 0.5f) * 1.25f + 0.5f)
            floatArrayOf(l, l, l)
        },
        "cinematic_teal" to { r, g, b ->
            val l = luma(r, g, b)
            // Warm highlights, teal shadows (classic teal & orange grade).
            floatArrayOf(c(r + (1f - l) * 0.12f), c(g + l * 0.03f), c(b + l * 0.10f - (1f - l) * 0.05f))
        },
        "cinematic_moody" to { r, g, b ->
            val l = luma(r, g, b)
            floatArrayOf(c(lerp(r, l, 0.30f) * 1.02f), c(lerp(g, l, 0.30f)), c(lerp(b, l, 0.30f) + 0.05f))
        },
    )

    private fun luma(r: Float, g: Float, b: Float): Float = 0.299f * r + 0.587f * g + 0.114f * b
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun c(v: Float): Float = v.coerceIn(0f, 1f)
    private fun to255(v: Float): Int = (v * 255f).toInt().coerceIn(0, 255)
}
