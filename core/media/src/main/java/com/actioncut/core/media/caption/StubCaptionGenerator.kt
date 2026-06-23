package com.actioncut.core.media.caption

import com.actioncut.core.domain.port.CaptionGenerator
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Phase 9 (bonus) stub for auto-captions. Returns deterministic placeholder cues so the
 * UI/integration can be built end-to-end before a real speech-to-text backend is wired.
 *
 * Honors the pluggable [CaptionGenerator.Config]:
 *  - [CaptionGenerator.Config.AzureManagedIdentity] is the intended default for cloud
 *    deployments — authenticate with `DefaultAzureCredential` (Managed Identity) and call
 *    Azure AI Speech batch transcription.
 *  - [CaptionGenerator.Config.ApiKey] supports users without managed-identity access.
 *  - [CaptionGenerator.Config.OnDevice] is the offline fallback used by this stub.
 */
class StubCaptionGenerator @Inject constructor() : CaptionGenerator {

    override fun isAvailable(config: CaptionGenerator.Config): Boolean = when (config) {
        is CaptionGenerator.Config.AzureManagedIdentity -> config.endpoint.isNotBlank()
        is CaptionGenerator.Config.ApiKey -> config.endpoint.isNotBlank() && config.key.isNotBlank()
        CaptionGenerator.Config.OnDevice -> true
    }

    override suspend fun generate(
        mediaUri: String,
        config: CaptionGenerator.Config,
        languageTag: String?,
    ): List<CaptionGenerator.Cue> {
        // Simulate async transcription latency.
        delay(300)
        val phrases = listOf(
            "Welcome to ActionCut",
            "Edit faster than ever",
            "Add captions in one tap",
            "Export in stunning quality",
        )
        return phrases.mapIndexed { index, text ->
            val start = index * 2_000L
            CaptionGenerator.Cue(
                startMs = start,
                endMs = start + 1_800L,
                text = text,
                confidence = 0.9f,
            )
        }
    }
}
