package com.actioncut.core.data.mediastore

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.actioncut.core.common.coroutine.DispatcherProvider
import com.actioncut.core.domain.port.MediaSaver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves exported videos into the shared `Movies/ActionCut` collection via MediaStore so
 * they appear in the Gallery and are shareable. Uses scoped-storage (`IS_PENDING`) on
 * API 29+, and the legacy public directory on API 26–28.
 */
@Singleton
class MediaStoreVideoSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : MediaSaver {

    override suspend fun saveVideoToGallery(
        sourceFilePath: String,
        displayName: String,
        mimeType: String,
    ): String? = withContext(dispatchers.io) {
        val source = File(sourceFilePath)
        if (!source.exists()) return@withContext null
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(source, displayName, mimeType)
            } else {
                saveLegacy(source, displayName, mimeType)
            }
        }.getOrNull()
    }

    private fun saveScoped(source: File, displayName: String, mimeType: String): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ActionCut")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    }

    private fun saveLegacy(source: File, displayName: String, mimeType: String): String? {
        @Suppress("DEPRECATION")
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val targetDir = File(moviesDir, "ActionCut").apply { mkdirs() }
        val target = File(targetDir, displayName)
        source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            @Suppress("DEPRECATION")
            put(MediaStore.Video.Media.DATA, target.absolutePath)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        return uri?.toString()
    }
}
