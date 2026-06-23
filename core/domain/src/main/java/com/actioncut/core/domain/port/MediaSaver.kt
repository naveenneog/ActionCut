package com.actioncut.core.domain.port

/**
 * Saves a rendered video file into the device's shared media collection (the Gallery),
 * returning a shareable `content://` URI. Implemented in `:core:data` via MediaStore so
 * exports show up in Photos/Gallery and can be shared to social apps without FileProvider.
 */
interface MediaSaver {
    /**
     * Copies [sourceFilePath] into `Movies/ActionCut` in the shared store.
     * @return the gallery content URI as a string, or null on failure.
     */
    suspend fun saveVideoToGallery(
        sourceFilePath: String,
        displayName: String,
        mimeType: String = "video/mp4",
    ): String?
}
