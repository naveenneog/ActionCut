package com.actioncut.core.model

/** Whether a built-in library track is background music or a one-shot sound effect. */
enum class AudioCategory { MUSIC, SFX }

/**
 * A bundled, royalty-free audio asset in ActionCut's built-in library. The actual audio
 * lives in `app/res/raw/<rawResName>.wav` (procedurally generated — see tools/gen_audio.py)
 * and is resolved to a playable file URI at add-time.
 */
data class LibraryTrack(
    val id: String,
    val title: String,
    val category: AudioCategory,
    val rawResName: String,
    val approxDurationMs: Long,
)

/** The catalogue shown in the editor's Music / SFX picker. */
object AudioLibrary {
    val tracks: List<LibraryTrack> = listOf(
        LibraryTrack("lofi", "Lo-Fi Chill", AudioCategory.MUSIC, "music_lofi", 6_400),
        LibraryTrack("upbeat", "Upbeat Pop", AudioCategory.MUSIC, "music_upbeat", 4_000),
        LibraryTrack("ambient", "Ambient Pad", AudioCategory.MUSIC, "music_ambient", 10_000),
        LibraryTrack("whoosh", "Whoosh", AudioCategory.SFX, "sfx_whoosh", 800),
        LibraryTrack("pop", "Pop", AudioCategory.SFX, "sfx_pop", 220),
        LibraryTrack("click", "Click", AudioCategory.SFX, "sfx_click", 60),
    )

    val music: List<LibraryTrack> get() = tracks.filter { it.category == AudioCategory.MUSIC }
    val sfx: List<LibraryTrack> get() = tracks.filter { it.category == AudioCategory.SFX }
}
