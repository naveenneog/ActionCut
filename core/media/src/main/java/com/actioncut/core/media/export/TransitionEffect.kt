package com.actioncut.core.media.export

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.actioncut.core.model.TransitionType

/** Which edge of a clip a transition animates. */
internal enum class TransitionEdge { IN, OUT }

/** The achievable per-clip transition behaviours (no neighbour compositing required). */
internal enum class TransitionKind { FADE, ZOOM, BLUR }

/**
 * A boundary transition rendered as a per-clip edge effect. Media3 Transformer plays clips
 * back-to-back without cross-clip compositing, so true A→B crossfades aren't possible; we
 * instead ramp the clip's own [edge]:
 *  - [TransitionKind.FADE] — fade through black (FADE/DISSOLVE, and the honest fallback for
 *    directional Slide/Wipe which would need the neighbouring frame).
 *  - [TransitionKind.ZOOM] — push in toward centre.
 *  - [TransitionKind.BLUR] — ramp a cheap box blur.
 *
 * Pairing an out-edge on clip A with an in-edge on clip B reads as a transition between them.
 */
internal class TransitionEffect(
    private val edge: TransitionEdge,
    private val kind: TransitionKind,
    private val edgeDurationUs: Long,
    private val clipDurationUs: Long,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        TransitionProgram(useHdr, edge, kind, edgeDurationUs, clipDurationUs)

    companion object {
        /** Maps a [TransitionType] to the closest achievable per-clip behaviour. */
        fun kindFor(type: TransitionType): TransitionKind = when (type) {
            TransitionType.ZOOM_IN, TransitionType.ZOOM_OUT -> TransitionKind.ZOOM
            TransitionType.BLUR -> TransitionKind.BLUR
            // Fade, dissolve, and directional slide/wipe (which need the neighbour frame)
            // all degrade to a fade-through-black at export.
            else -> TransitionKind.FADE
        }
    }
}

private class TransitionProgram(
    useHdr: Boolean,
    private val edge: TransitionEdge,
    private val kind: TransitionKind,
    private val edgeDurationUs: Long,
    private val clipDurationUs: Long,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private var width = 1
    private var height = 1

    init {
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        // f = 1 fully visible, 0 fully transitioned, ramped only within the edge window.
        val t = presentationTimeUs.coerceIn(0L, clipDurationUs.coerceAtLeast(1L))
        val f = when (edge) {
            TransitionEdge.IN ->
                if (edgeDurationUs > 0L) (t.toFloat() / edgeDurationUs).coerceIn(0f, 1f) else 1f
            TransitionEdge.OUT ->
                if (edgeDurationUs > 0L) ((clipDurationUs - t).toFloat() / edgeDurationUs).coerceIn(0f, 1f) else 1f
        }
        val alpha: Float
        val scale: Float
        val blur: Float
        when (kind) {
            TransitionKind.FADE -> { alpha = f; scale = 1f; blur = 0f }
            TransitionKind.ZOOM -> { alpha = 1f; scale = 1f + (1f - f) * 0.25f; blur = 0f }
            TransitionKind.BLUR -> { alpha = 1f; scale = 1f; blur = (1f - f) * 8f }
        }
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatsUniformIfPresent("uAlpha", floatArrayOf(alpha))
            program.setFloatsUniformIfPresent("uScale", floatArrayOf(scale))
            program.setFloatsUniformIfPresent("uBlur", floatArrayOf(blur))
            program.setFloatsUniformIfPresent("uResolution", floatArrayOf(width.toFloat(), height.toFloat()))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
              gl_Position = aFramePosition;
              vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
            }
        """

        const val FRAGMENT_SHADER = """
            #version 100
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform float uAlpha;
            uniform float uScale;
            uniform float uBlur;
            uniform vec2 uResolution;
            varying vec2 vTexSamplingCoord;
            void main() {
              vec2 uv = (vTexSamplingCoord - 0.5) / uScale + 0.5; // zoom toward centre
              vec4 c;
              if (uBlur > 0.0) {
                vec2 px = uBlur / uResolution;
                c  = texture2D(uTexSampler, uv);
                c += texture2D(uTexSampler, uv + vec2(px.x, 0.0));
                c += texture2D(uTexSampler, uv - vec2(px.x, 0.0));
                c += texture2D(uTexSampler, uv + vec2(0.0, px.y));
                c += texture2D(uTexSampler, uv - vec2(0.0, px.y));
                c /= 5.0;
              } else {
                c = texture2D(uTexSampler, uv);
              }
              gl_FragColor = vec4(c.rgb * uAlpha, c.a);
            }
        """
    }
}
