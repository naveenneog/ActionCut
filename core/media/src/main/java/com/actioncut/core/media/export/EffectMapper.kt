package com.actioncut.core.media.export

import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.CropRect
import com.actioncut.core.model.VisualEffect
import com.actioncut.core.model.VisualEffectType

/**
 * Translates a [Clip]'s editing properties into a Media3 GPU [Effect] chain (and audio
 * processors) applied at export. This is the bridge between the editor's data model and
 * the hardware-accelerated render pipeline.
 *
 * Effect order is deliberate: geometry (rotate → crop) → colour (brightness → contrast →
 * saturation → warmth → LUT) → stylistic (blur) → speed → output [Presentation] last so
 * every clip is normalized to the same export dimensions for concatenation.
 */
object EffectMapper {

    fun videoEffects(clip: Clip, targetWidth: Int, targetHeight: Int): List<Effect> =
        videoEffects(clip, targetWidth, targetHeight, emptyList())

    fun videoEffects(
        clip: Clip,
        targetWidth: Int,
        targetHeight: Int,
        overlays: List<Clip>,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        // --- Geometry ---
        val rotation = clip.rotationDegrees + clip.transform.rotationDegrees
        if (rotation % 360f != 0f) {
            effects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(rotation)
                .build()
        }
        clip.crop?.takeUnless { it.isFull }?.let { effects += cropEffect(it) }

        // --- Colour adjustments ---
        val adj = clip.adjustments
        if (adj.brightness != 0f) effects += Brightness(adj.brightness.coerceIn(-1f, 1f))
        if (adj.contrast != 0f) effects += Contrast(adj.contrast.coerceIn(-1f, 1f))
        if (adj.saturation != 0f) {
            effects += HslAdjustment.Builder()
                .adjustSaturation((adj.saturation * 100f).coerceIn(-100f, 100f))
                .build()
        }
        if (adj.warmth != 0f) {
            val w = adj.warmth.coerceIn(-1f, 1f)
            effects += RgbAdjustment.Builder()
                .setRedScale(1f + w * 0.3f)
                .setBlueScale(1f - w * 0.3f)
                .build()
        }

        // --- Filter (real 3D LUT) ---
        clip.filter?.let { filter ->
            FilterLuts.cubeFor(filter.id, filter.intensity)?.let { cube ->
                effects += SingleColorLut.createFromCube(cube)
            }
        }

        // --- Stylistic visual effects ---
        clip.effects.forEach { ve -> visualEffect(ve)?.let { effects += it } }

        // --- Speed (video) ---
        if (clip.speed != 1f && clip.type == ClipType.VIDEO) {
            effects += SpeedChangeEffect(clip.speed)
        }

        // --- Output scaling ---
        effects += Presentation.createForWidthAndHeight(
            targetWidth,
            targetHeight,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
        )

        // --- Sticker / text overlays (applied last, on the framed output) ---
        overlayEffect(clip, overlays)?.let { effects += it }
        return effects
    }

    /** Builds a Media3 [OverlayEffect] for sticker/text clips overlapping [clip]'s span. */
    private fun overlayEffect(clip: Clip, overlays: List<Clip>): Effect? {
        val active = overlays.filter { ov ->
            !ov.text?.text.isNullOrEmpty() &&
                ov.timelineStartMs < clip.timelineEndMs &&
                ov.timelineEndMs > clip.timelineStartMs
        }
        if (active.isEmpty()) return null

        val textureOverlays: List<TextureOverlay> = active.map { ov ->
            val props = ov.text!!
            val span = SpannableString(props.text)
            val sizePx = (props.fontSizeSp * ov.transform.scale * 2.5f).toInt().coerceAtLeast(24)
            span.setSpan(AbsoluteSizeSpan(sizePx), 0, span.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            span.setSpan(ForegroundColorSpan(props.colorArgb), 0, span.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            val settings = OverlaySettings.Builder()
                .setBackgroundFrameAnchor(
                    ov.transform.offsetX.coerceIn(-1f, 1f),
                    -ov.transform.offsetY.coerceIn(-1f, 1f),
                )
                .setRotationDegrees(ov.transform.rotationDegrees)
                .build()
            TextOverlay.createStaticTextOverlay(span, settings)
        }
        return OverlayEffect(ImmutableList.copyOf(textureOverlays))
    }

    /**
     * Per-clip audio processors. Keeps audio in sync when a clip's speed changes and
     * applies per-clip volume/gain. A muted clip (volume == 0) is handled by the exporter
     * via `EditedMediaItem.setRemoveAudio(true)` instead, so it's skipped here.
     */
    fun audioProcessors(clip: Clip): List<AudioProcessor> {
        val processors = mutableListOf<AudioProcessor>()
        if (clip.speed != 1f && (clip.type == ClipType.VIDEO || clip.type == ClipType.AUDIO)) {
            processors += SonicAudioProcessor().apply { setSpeed(clip.speed) }
        }
        val volume = clip.volume
        if (volume != 1f && volume != 0f) {
            processors += ChannelMixingAudioProcessor().apply {
                // Cover both mono and stereo inputs; the processor picks by channel count.
                putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(volume))
                putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(volume))
            }
        }
        return processors
    }

    private fun cropEffect(crop: CropRect): Crop {
        // Normalized crop (0..1, origin top-left) -> Media3 NDC (-1..1, origin centre, y-up).
        val left = crop.left * 2f - 1f
        val right = crop.right * 2f - 1f
        val top = 1f - crop.top * 2f
        val bottom = 1f - crop.bottom * 2f
        return Crop(left, right, bottom, top)
    }

    private fun visualEffect(effect: VisualEffect): Effect? = when (effect.type) {
        VisualEffectType.GAUSSIAN_BLUR,
        VisualEffectType.BOKEH,
        -> GaussianBlur(1f + effect.intensity * 8f)
        VisualEffectType.RADIAL_BLUR -> GaussianBlur(1f + effect.intensity * 6f)
        // Stylized/retro looks (glitch, VHS, grain, pixelate, …) have no stock Media3
        // effect; they're skipped at export until custom GL shaders are added.
        else -> null
    }
}
