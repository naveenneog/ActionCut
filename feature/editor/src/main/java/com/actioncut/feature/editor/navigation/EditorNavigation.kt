package com.actioncut.feature.editor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.actioncut.feature.editor.ui.EditorScreen
import com.actioncut.feature.editor.ui.EditorViewModel

private const val EDITOR_BASE = "editor"
const val EDITOR_ROUTE = "$EDITOR_BASE/{${EditorViewModel.ARG_PROJECT_ID}}"

fun NavController.navigateToEditor(
    projectId: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate("$EDITOR_BASE/$projectId", builder)

/** Registers the editor destination, reading the `projectId` path argument. */
fun NavGraphBuilder.editorScreen(
    onBack: () -> Unit,
    onExport: (projectId: String) -> Unit,
) {
    composable(
        route = EDITOR_ROUTE,
        arguments = listOf(navArgument(EditorViewModel.ARG_PROJECT_ID) { type = NavType.StringType }),
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString(EditorViewModel.ARG_PROJECT_ID).orEmpty()
        EditorScreen(
            onBack = onBack,
            onExport = { onExport(projectId) },
        )
    }
}
