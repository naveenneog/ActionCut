package com.actioncut.core.media.audio

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a voiceover from the device microphone into an AAC/MP4 file in the app cache.
 * The resulting `file://` URI is added to the timeline like any other audio clip.
 *
 * The caller must hold the `RECORD_AUDIO` runtime permission before calling [start];
 * failures (no permission, mic busy) are swallowed and surfaced as a false/null result.
 */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Begins recording. Returns true if recording actually started. */
    fun start(): Boolean {
        if (recorder != null) return true
        val file = File(context.cacheDir, "voiceover_${System.currentTimeMillis()}.m4a")
        return runCatching {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            outputFile = file
            true
        }.getOrElse {
            runCatching { recorder?.release() }
            recorder = null
            outputFile = null
            false
        }
    }

    /** Stops recording and returns the recorded file URI, or null if nothing usable. */
    fun stop(): String? {
        val r = recorder ?: return null
        val file = outputFile
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        recorder = null
        outputFile = null
        return if (ok && file != null && file.length() > 0L) Uri.fromFile(file).toString() else null
    }

    /** Aborts recording and discards the partial file. */
    fun cancel() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
