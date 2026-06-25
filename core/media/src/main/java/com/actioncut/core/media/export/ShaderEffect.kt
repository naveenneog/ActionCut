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

/**
 * A reusable custom-GL [GlEffect] that runs a single fragment shader over each frame. This
 * is how ActionCut implements stylized looks (glitch, VHS, film grain, pixelate, …) that
 * have no stock Media3 effect.
 *
 * Every fragment shader is fed a uniform contract (see [Shaders]):
 *  - `sampler2D uTexSampler` — the input frame
 *  - `float uIntensity`      — effect strength 0..1
 *  - `float uTime`           — presentation time in seconds (drives animation)
 *  - `vec2 uResolution`      — frame size in pixels
 *  - `vec2 vTexSamplingCoord`— interpolated 0..1 sampling coordinate
 *
 * Note: the GLSL itself can only be verified on-device; it is written to the standard
 * Media3 single-attribute contract used by the official Transformer demo shaders.
 */
class ShaderEffect(
    private val fragmentShader: String,
    private val intensity: Float,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ShaderProgram(useHdr, fragmentShader, intensity)
}

/** Runs [ShaderEffect]'s fragment shader against a full-screen quad. */
private class ShaderProgram(
    useHdr: Boolean,
    fragmentShader: String,
    private val intensity: Float,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = GlProgram(VERTEX_SHADER, fragmentShader)
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
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            // Use *IfPresent for the optional uniforms: a shader that doesn't reference one
            // (e.g. GLITCH ignores uResolution, PIXELATE ignores uTime) has it optimized out
            // by the GLSL compiler, and setting an absent uniform throws inside GlProgram.
            program.setFloatsUniformIfPresent("uIntensity", floatArrayOf(intensity.coerceIn(0f, 1f)))
            program.setFloatsUniformIfPresent("uTime", floatArrayOf((presentationTimeUs / 1_000_000.0).toFloat()))
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
        // Single-attribute passthrough vertex shader: derive 0..1 tex coords from the
        // [-1,1] clip-space quad supplied by GlUtil.getNormalizedCoordinateBounds().
        const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
              gl_Position = aFramePosition;
              vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
            }
        """
    }
}
