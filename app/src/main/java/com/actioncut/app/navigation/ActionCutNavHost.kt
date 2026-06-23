package com.actioncut.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.actioncut.app.ui.HOME_ROUTE
import com.actioncut.app.ui.homeScreen
import com.actioncut.feature.editor.navigation.editorScreen
import com.actioncut.feature.editor.navigation.navigateToEditor
import com.actioncut.feature.export.navigation.exportScreen
import com.actioncut.feature.export.navigation.navigateToExport
import com.actioncut.feature.media.navigation.MEDIA_PICKER_ROUTE
import com.actioncut.feature.media.navigation.mediaPickerScreen
import com.actioncut.feature.media.navigation.navigateToMediaPicker

/**
 * Top-level navigation graph wiring the feature modules together:
 * Home → Media Picker → Editor → Export.
 */
@Composable
fun ActionCutNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        homeScreen(
            onNewProject = { navController.navigateToMediaPicker() },
            onOpenProject = { projectId -> navController.navigateToEditor(projectId) },
        )

        mediaPickerScreen(
            onProjectCreated = { projectId ->
                navController.navigateToEditor(projectId) {
                    // Don't keep the picker in the back stack once a project is created.
                    popUpTo(MEDIA_PICKER_ROUTE) { inclusive = true }
                }
            },
            onBack = { navController.popBackStack() },
        )

        editorScreen(
            onBack = { navController.popBackStack() },
            onExport = { projectId -> navController.navigateToExport(projectId) },
        )

        exportScreen(onBack = { navController.popBackStack() })
    }
}
