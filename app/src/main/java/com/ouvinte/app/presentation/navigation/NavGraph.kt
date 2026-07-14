package com.ouvinte.app.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ouvinte.app.data.repository.SettingsRepository
import com.ouvinte.app.presentation.analysis.AnalysisScreen
import com.ouvinte.app.presentation.home.HomeScreen
import com.ouvinte.app.presentation.pin.PinScreen
import com.ouvinte.app.presentation.project.ProjectScreen
import com.ouvinte.app.presentation.questions.QuestionsScreen
import com.ouvinte.app.presentation.recording.RecordingScreen
import com.ouvinte.app.presentation.session.SessionScreen
import com.ouvinte.app.presentation.settings.SettingsScreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

sealed class Screen(val route: String) {
    object Pin : Screen("pin/{isSetup}") {
        fun createRoute(isSetup: Boolean) = "pin/$isSetup"
    }
    object Settings : Screen("settings/{isFirstSetup}") {
        fun createRoute(isFirstSetup: Boolean) = "settings/$isFirstSetup"
    }
    object Home : Screen("home")
    object Recording : Screen("recording")
    object Session : Screen("session/{sessionId}") {
        fun createRoute(sessionId: Long) = "session/$sessionId"
    }
    object Analysis : Screen("analysis/{sessionId}") {
        fun createRoute(sessionId: Long) = "analysis/$sessionId"
    }
    object Questions : Screen("questions/{sessionId}") {
        fun createRoute(sessionId: Long) = "questions/$sessionId"
    }
    object Project : Screen("project/{projectId}/{projectName}") {
        fun createRoute(projectId: Long, projectName: String) =
            "project/$projectId/${Uri.encode(projectName)}"
    }
}

@Composable
fun OuvinteNavGraph(settingsRepository: SettingsRepository) {
    val navController = rememberNavController()

    val isPinSet = remember { runBlocking { settingsRepository.isPinSet.first() } }
    val areKeysSet = remember { runBlocking { settingsRepository.areKeysSet.first() } }

    val startDestination = when {
        !isPinSet -> Screen.Pin.createRoute(true)
        !areKeysSet -> Screen.Settings.createRoute(true)
        else -> Screen.Pin.createRoute(false)
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(
            route = Screen.Pin.route,
            arguments = listOf(navArgument("isSetup") { type = NavType.BoolType })
        ) { backStackEntry ->
            val isSetup = backStackEntry.arguments!!.getBoolean("isSetup")
            PinScreen(
                isSetup = isSetup,
                onSuccess = {
                    if (isSetup) {
                        navController.navigate(Screen.Settings.createRoute(true)) {
                            popUpTo(Screen.Pin.createRoute(true)) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Pin.createRoute(false)) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.Settings.route,
            arguments = listOf(navArgument("isFirstSetup") { type = NavType.BoolType })
        ) { backStackEntry ->
            val isFirst = backStackEntry.arguments!!.getBoolean("isFirstSetup")
            SettingsScreen(
                isFirstSetup = isFirst,
                onSaved = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = if (!isFirst) ({ navController.popBackStack() }) else null
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNewSession = { navController.navigate(Screen.Recording.route) },
                onOpenSession = { id -> navController.navigate(Screen.Session.createRoute(id)) },
                onOpenProject = { id, name ->
                    navController.navigate(Screen.Project.createRoute(id, name))
                },
                onSettings = { navController.navigate(Screen.Settings.createRoute(false)) }
            )
        }

        composable(Screen.Recording.route) {
            RecordingScreen(
                onRecordingFinished = { sessionId ->
                    navController.navigate(Screen.Session.createRoute(sessionId)) {
                        popUpTo(Screen.Recording.route) { inclusive = true }
                    }
                },
                onRecordingFinishedProject = { projectId ->
                    navController.navigate(Screen.Project.createRoute(projectId, "Gravação")) {
                        popUpTo(Screen.Recording.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Session.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments!!.getLong("sessionId")
            SessionScreen(
                sessionId = sessionId,
                onAnalyse = { navController.navigate(Screen.Analysis.createRoute(sessionId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Analysis.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments!!.getLong("sessionId")
            AnalysisScreen(
                sessionId = sessionId,
                onQuestions = { navController.navigate(Screen.Questions.createRoute(sessionId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Questions.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments!!.getLong("sessionId")
            QuestionsScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Project.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("projectName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments!!.getLong("projectId")
            val projectName = backStackEntry.arguments!!.getString("projectName") ?: ""
            ProjectScreen(
                projectId = projectId,
                projectName = projectName,
                onOpenSession = { id -> navController.navigate(Screen.Session.createRoute(id)) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
