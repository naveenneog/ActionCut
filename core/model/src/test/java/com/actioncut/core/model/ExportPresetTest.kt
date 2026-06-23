package com.actioncut.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportPresetTest {

    @Test
    fun preset_pinsAspectAndResolution() {
        val base = ExportSettings()
        val reel = ExportPreset.INSTAGRAM_REEL.applyTo(base)
        assertEquals(AspectRatio.RATIO_9_16, reel.aspectRatio)
        assertEquals(Resolution.P1080, reel.resolution)

        val square = ExportPreset.INSTAGRAM_POST.applyTo(base)
        assertEquals(AspectRatio.RATIO_1_1, square.aspectRatio)

        val fourK = ExportPreset.YOUTUBE_4K.applyTo(base)
        assertEquals(Resolution.P2160, fourK.resolution)
        assertEquals(AspectRatio.RATIO_16_9, fourK.aspectRatio)
    }

    @Test
    fun original_keepsProjectAspect() {
        val applied = ExportPreset.ORIGINAL.applyTo(ExportSettings())
        // Null aspect = "use the project's own aspect ratio" downstream.
        assertNull(applied.aspectRatio)
    }
}
