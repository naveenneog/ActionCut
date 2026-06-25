package com.actioncut.feature.editor.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies the contextual clip action bar exposes a working, discoverable **Delete** (and
 * Duplicate) — the fix for "there is no delete option once a video/image is added".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SelectedClipBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deleteButton_isPresentAndFires() {
        var deleted = false
        compose.setContent {
            SelectedClipBar(
                onSplit = {},
                onDuplicate = {},
                onDelete = { deleted = true },
                onDeselect = {},
            )
        }

        compose.onNodeWithText("Delete").performClick()

        assertTrue("Delete action should fire from the contextual clip bar", deleted)
    }

    @Test
    fun duplicateButton_fires() {
        var duplicated = false
        compose.setContent {
            SelectedClipBar(
                onSplit = {},
                onDuplicate = { duplicated = true },
                onDelete = {},
                onDeselect = {},
            )
        }

        compose.onNodeWithText("Duplicate").performClick()

        assertTrue(duplicated)
    }
}
