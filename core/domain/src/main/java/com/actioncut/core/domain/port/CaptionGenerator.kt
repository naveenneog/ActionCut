package com.actioncut.core.domain.port

/**
 * Phase 9 (bonus) — Auto-captions port. Generates timed captions from a media file's
 * audio track. Implemented out-of-band by a speech-to-text provider.
 *
 * Designed to be pluggable: the default cloud adapter authenticates with Azure using
 * `DefaultAzureCredential` (Managed Identity), while other deployments can supply an
 * API key via configuration. Implementations live outside the domain layer.
 */
interface CaptionGenerator {

    /** A single timed caption cue. */
    data class Cue(
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val confidence: Float = 1f,
    )

    /** Provider/credential configuration for caption generation. */
    sealed interface Config {
        /** Use Azure Speech with DefaultAzureCredential / Managed Identity. */
        data class AzureManagedIdentity(
            val endpoint: String,
            val region: String,
        ) : Config

        /** Use an explicit API key (for users without managed identity access). */
        data class ApiKey(
            val endpoint: String,
            val region: String,
            val key: String,
        ) : Config

        /** On-device / offline stub (no network). */
        data object OnDevice : Config
    }

    /** Whether captioning is available for the current [config]. */
    fun isAvailable(config: Config): Boolean

    /**
     * Transcribes the audio of [mediaUri] into ordered [Cue]s.
     * @param languageTag BCP-47 language tag, or null to auto-detect.
     */
    suspend fun generate(
        mediaUri: String,
        config: Config,
        languageTag: String? = null,
    ): List<Cue>
}
