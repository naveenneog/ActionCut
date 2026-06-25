package com.actioncut.core.media.reverse

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverses a clip by extracting its frames newest→oldest and re-encoding them to a temp MP4.
 * Media3 Transformer has no temporal reverse, so the exporter pre-processes reversed clips
 * with this and then treats the result as an ordinary forward clip.
 *
 * Frames are decoded with [MediaMetadataRetriever] (codec-agnostic, avoids decoder colour-
 * format pitfalls) and encoded through an [MediaCodec] input Surface via a tiny GL blit.
 * Audio is dropped from reversed clips (reversed audio is rarely wanted and is costly).
 */
@Singleton
class VideoReverser @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Produces a reversed MP4 of [uri] over the [startMs, endMs] window and returns its
     * `file://` URI, or null on failure (caller falls back to the original clip).
     */
    fun reverse(uri: String, startMs: Long, endMs: Long, fps: Int = 24): String? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, Uri.parse(uri))
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val inMs = startMs.coerceIn(0L, durationMs)
            val outMs = (if (endMs > startMs) endMs else durationMs).coerceIn(inMs, durationMs)
            val windowMs = (outMs - inMs).coerceAtLeast(1L)
            val frameCount = ((windowMs * fps) / 1000L).toInt().coerceIn(1, 600)

            val firstFrame = retriever.getFrameAtTime(inMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(inMs * 1000)
                ?: return null
            val width = even(firstFrame.width)
            val height = even(firstFrame.height)
            firstFrame.recycle()

            val outFile = File(context.cacheDir, "reversed_${uri.hashCode()}_${startMs}_$endMs.mp4")
            if (outFile.exists()) outFile.delete()

            encodeReversed(retriever, outFile, width, height, fps, frameCount, inMs, outMs)
            Uri.fromFile(outFile).toString()
        }.getOrNull().also { runCatching { retriever.release() } }
    }

    private fun encodeReversed(
        retriever: MediaMetadataRetriever,
        outFile: File,
        width: Int,
        height: Int,
        fps: Int,
        frameCount: Int,
        inMs: Long,
        outMs: Long,
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width.toLong() * height * fps / 5).toInt().coerceAtLeast(2_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val gl = GlSurface(encoder.createInputSurface())
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameDurUs = 1_000_000L / fps

        fun drain(endOfStream: Boolean) {
            if (endOfStream) encoder.signalEndOfInputStream()
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIndex >= 0) {
                    val encoded = encoder.getOutputBuffer(outIndex)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bufferInfo.size = 0
                    if (bufferInfo.size != 0 && muxerStarted) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        try {
            for (i in 0 until frameCount) {
                // Newest → oldest: walk the source window backwards.
                val frac = if (frameCount <= 1) 0f else i.toFloat() / (frameCount - 1)
                val tMs = outMs - (frac * (outMs - inMs)).toLong()
                val bmp = retriever.getFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: continue
                gl.drawBitmap(bmp, i * frameDurUs * 1000)
                bmp.recycle()
                drain(false)
            }
            drain(true)
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { gl.release() }
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun even(v: Int) = if (v % 2 == 0) v else v - 1

    /** Minimal EGL + GLES2 surface that blits a Bitmap (flipped) onto a codec input Surface. */
    private class GlSurface(surface: android.view.Surface) {
        private val display: EGLDisplay
        private val context: EGLContext
        private val eglSurface: EGLSurface
        private val program: Int
        private val texId: Int
        private val quad: FloatBuffer

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)
            val cfg = arrayOfNulls<EGLConfig>(1)
            EGL14.eglChooseConfig(
                display,
                intArrayOf(
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    0x3142, 1, // EGL_RECORDABLE_ANDROID
                    EGL14.EGL_NONE,
                ),
                0, cfg, 0, 1, IntArray(1), 0,
            )
            context = EGL14.eglCreateContext(
                display, cfg[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            eglSurface = EGL14.eglCreateWindowSurface(display, cfg[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)

            program = buildProgram()
            val tex = IntArray(1); GLES20.glGenTextures(1, tex, 0); texId = tex[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            // Full-screen quad: x, y, u, v (v flipped so the bitmap is upright).
            val verts = floatArrayOf(
                -1f, -1f, 0f, 1f,
                1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,
                1f, 1f, 1f, 0f,
            )
            quad = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            quad.put(verts).position(0)
        }

        fun drawBitmap(bitmap: Bitmap, ptsNs: Long) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            val pos = GLES20.glGetAttribLocation(program, "aPos")
            val tex = GLES20.glGetAttribLocation(program, "aTex")
            quad.position(0); GLES20.glEnableVertexAttribArray(pos)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad)
            quad.position(2); GLES20.glEnableVertexAttribArray(tex)
            GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, ptsNs)
            EGL14.eglSwapBuffers(display, eglSurface)
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }

        private fun buildProgram(): Int {
            val vs = compile(
                GLES20.GL_VERTEX_SHADER,
                "attribute vec2 aPos; attribute vec2 aTex; varying vec2 vTex;" +
                    "void main(){vTex=aTex; gl_Position=vec4(aPos,0.0,1.0);}",
            )
            val fs = compile(
                GLES20.GL_FRAGMENT_SHADER,
                "precision mediump float; varying vec2 vTex; uniform sampler2D uTex;" +
                    "void main(){gl_FragColor=texture2D(uTex,vTex);}",
            )
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
            return p
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
            return s
        }
    }
}
