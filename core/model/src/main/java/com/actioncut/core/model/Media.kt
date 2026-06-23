package com.actioncut.core.model

/**
 * The kind of media an item or clip represents.
 */
enum class MediaType {
    VIDEO,
    IMAGE,
    AUDIO,
}

/**
 * A single piece of media discovered on the device (via MediaStore / scoped storage).
 *
 * URIs are stored as [String] rather than `android.net.Uri` so that this module stays
 * platform-agnostic (pure Kotlin) and trivially unit-testable.
 *
 * @property id Stable identifier (MediaStore id encoded as string).
 * @property uri Content URI string used to open the media.
 * @property type Whether this is a video, image or audio file.
 * @property displayName Human-readable file name.
 * @property durationMs Duration for video/audio; 0 for images.
 * @property width Pixel width (0 if unknown / audio).
 * @property height Pixel height (0 if unknown / audio).
 * @property sizeBytes File size in bytes.
 * @property dateAddedEpochSec When the file was added, in epoch seconds.
 * @property folderName Bucket / album the media belongs to.
 */
data class MediaItem(
    val id: String,
    val uri: String,
    val type: MediaType,
    val displayName: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val dateAddedEpochSec: Long = 0L,
    val folderName: String = "",
) {
    val isVideo: Boolean get() = type == MediaType.VIDEO
    val isImage: Boolean get() = type == MediaType.IMAGE
    val isAudio: Boolean get() = type == MediaType.AUDIO

    /** Aspect ratio (w/h) of the media, or 0 when unknown. */
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 0f
}
