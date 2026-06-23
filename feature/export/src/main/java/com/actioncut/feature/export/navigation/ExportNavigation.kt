package com.actioncut.feature.export.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.actioncut.feature.export.ui.ExportScreen
import com.actioncut.feature.export.ui.ExportViewModel

private const val EXPORT_BASE = "export"
const val EXPORT_ROUTE = "$EXPORT_BASE/{${ExportViewModel.ARG_PROJECT_ID}}"

fun NavController.navigateToExport(projectId: String) = navigate("$EXPORT_BASE/$projectId")

fun NavGraphBuilder.exportScreen(onBack: () -> Unit) {
    composable(
        route = EXPORT_ROUTE,
        arguments = listOf(navArgument(ExportViewModel.ARG_PROJECT_ID) { type = NavType.StringType }),
    ) {
        ExportScreen(onBack = onBack)
    }
}
