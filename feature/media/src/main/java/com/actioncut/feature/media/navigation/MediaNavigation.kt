package com.actioncut.feature.media.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.actioncut.feature.media.ui.MediaPickerScreen

const val MEDIA_PICKER_ROUTE = "media_picker"

fun NavController.navigateToMediaPicker() = navigate(MEDIA_PICKER_ROUTE)

/** Registers the media picker destination. */
fun NavGraphBuilder.mediaPickerScreen(
    onProjectCreated: (projectId: String) -> Unit,
    onBack: () -> Unit,
) {
    composable(MEDIA_PICKER_ROUTE) {
        MediaPickerScreen(onProjectCreated = onProjectCreated, onBack = onBack)
    }
}
