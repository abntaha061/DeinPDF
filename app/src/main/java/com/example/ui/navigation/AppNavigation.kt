package com.example.ui.navigation

import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.example.ui.bookmarks.BookmarksScreen
import com.example.ui.home.HomeScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.reader.ReaderScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.stats.StatsScreen
import com.example.ui.tools.PdfToolsScreen
import com.example.ui.vocabulary.VocabularyScreen
import com.example.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Onboarding  : Screen("onboarding")
    object Home        : Screen("home")
    object Library     : Screen("library")
    object Bookmarks   : Screen("bookmarks")
    object Settings    : Screen("settings")
    object Vocabulary  : Screen("vocabulary")
    object Tools       : Screen("tools")
    object Stats       : Screen("stats")
    object Reader      : Screen("reader/{encodedUri}/{pdfId}") {
        fun createRoute(encodedUri: String, pdfId: Long) = "reader/$encodedUri/$pdfId"
    }
}

@Composable
fun AppNavigation(
    intentUri: Uri?,
    viewModel: MainViewModel
) {
    val navController = rememberNavController()
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState()
    val startDestination = if (isFirstLaunch) Screen.Onboarding.route else Screen.Home.route

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    viewModel.setFirstLaunchDone()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenPdf = { uri, pdfId ->
                    navController.navigate(Screen.Reader.createRoute(Uri.encode(uri.toString()), pdfId))
                },
                onNavigateToLibrary   = { navController.navigate(Screen.Library.route) },
                onNavigateToBookmarks = { navController.navigate(Screen.Bookmarks.route) },
                onNavigateToSettings  = { navController.navigate(Screen.Settings.route) },
                onNavigateToVocabulary = { navController.navigate(Screen.Vocabulary.route) },
                onNavigateToTools     = { navController.navigate(Screen.Tools.route) },
                onNavigateToStats     = { navController.navigate(Screen.Stats.route) },
                viewModel = viewModel
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onOpenPdf = { uri, pdfId ->
                    navController.navigate(Screen.Reader.createRoute(Uri.encode(uri.toString()), pdfId))
                },
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        composable(Screen.Bookmarks.route) {
            BookmarksScreen(
                onOpenPdf = { uri, pdfId ->
                    navController.navigate(Screen.Reader.createRoute(Uri.encode(uri.toString()), pdfId))
                },
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        composable(Screen.Vocabulary.route) {
            VocabularyScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        composable(Screen.Tools.route) {
            PdfToolsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("encodedUri") { type = NavType.StringType },
                navArgument("pdfId")     { type = NavType.LongType }
            )
        ) { backStack ->
            val encodedUri = backStack.arguments?.getString("encodedUri") ?: ""
            val pdfId      = backStack.arguments?.getLong("pdfId") ?: 0L
            val uri = try {
                val parsed = Uri.parse(encodedUri)
                if (parsed.scheme != null && (parsed.scheme == "content" || parsed.scheme == "file")) {
                    parsed
                } else {
                    Uri.parse(Uri.decode(encodedUri))
                }
            } catch (e: Exception) {
                Uri.parse(Uri.decode(encodedUri))
            }
            ReaderScreen(
                pdfUri  = uri,
                pdfId   = pdfId,
                onBack  = { navController.popBackStack() },
                onNavigateToVocabulary = { navController.navigate(Screen.Vocabulary.route) }
            )
        }
    }

    LaunchedEffect(intentUri) {
        intentUri?.let { uri ->
            navController.navigate(Screen.Reader.createRoute(Uri.encode(uri.toString()), 0L))
        }
    }
}
