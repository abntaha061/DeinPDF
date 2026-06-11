package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

fun safeEncodeUri(uri: Uri): String {
    return android.util.Base64.encodeToString(
        uri.toString().toByteArray(Charsets.UTF_8),
        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
    )
}

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

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val isFab: Boolean = false
)

val bottomNavItems = listOf(
    BottomNavItem("Settings", Icons.Default.Settings, Screen.Settings.route),
    BottomNavItem("Tools", Icons.Default.Build, Screen.Tools.route, isFab = true),
    BottomNavItem("Folders", Icons.Default.Folder, Screen.Library.route),
    BottomNavItem("Home", Icons.Default.Home, Screen.Home.route),
)

val bottomNavRoutes = bottomNavItems.map { it.route }.toSet()

@Composable
fun AppNavigation(
    intentUri: Uri?,
    viewModel: MainViewModel
) {
    val navController = rememberNavController()
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState()
    val startDestination = if (isFirstLaunch) Screen.Onboarding.route else Screen.Home.route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    navController = navController
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {

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
                        navController.navigate(Screen.Reader.createRoute(safeEncodeUri(uri), pdfId))
                    },
                    onNavigateToLibrary    = { navController.navigate(Screen.Library.route) },
                    onNavigateToBookmarks  = { navController.navigate(Screen.Bookmarks.route) },
                    onNavigateToSettings   = { navController.navigate(Screen.Settings.route) },
                    onNavigateToVocabulary = { navController.navigate(Screen.Vocabulary.route) },
                    onNavigateToTools      = { navController.navigate(Screen.Tools.route) },
                    onNavigateToStats      = { navController.navigate(Screen.Stats.route) },
                    viewModel = viewModel
                )
            }

            composable(Screen.Library.route) {
                LibraryScreen(
                    onOpenPdf = { uri, pdfId ->
                        navController.navigate(Screen.Reader.createRoute(safeEncodeUri(uri), pdfId))
                    },
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel
                )
            }

            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    onOpenPdf = { uri, pdfId ->
                        navController.navigate(Screen.Reader.createRoute(safeEncodeUri(uri), pdfId))
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
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel
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
                    if (encodedUri.startsWith("content%3A") || encodedUri.startsWith("content://") || encodedUri.startsWith("file://")) {
                        Uri.parse(Uri.decode(encodedUri))
                    } else {
                        val decodedBytes = android.util.Base64.decode(encodedUri, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        Uri.parse(String(decodedBytes, Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    try { Uri.parse(Uri.decode(encodedUri)) }
                    catch (e2: Exception) { Uri.parse(encodedUri) }
                }
                ReaderScreen(
                    pdfUri  = uri,
                    pdfId   = pdfId,
                    onBack  = { navController.popBackStack() },
                    onNavigateToVocabulary = { navController.navigate(Screen.Vocabulary.route) }
                )
            }
        }
    }

    LaunchedEffect(intentUri) {
        intentUri?.let { uri ->
            navController.navigate(Screen.Reader.createRoute(safeEncodeUri(uri), 0L))
        }
    }
}

@Composable
fun AppBottomBar(
    currentRoute: String?,
    navController: NavController
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        // الشريط الرئيسي
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { item ->
                    if (item.isFab) {
                        // مساحة فارغة للـ FAB
                        Spacer(modifier = Modifier.width(56.dp))
                    } else {
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }

        // FAB في المنتصف بيطلع فوق الشريط
        FloatingActionButton(
            onClick = {
                navController.navigate(Screen.Tools.route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.TopCenter)
                .clip(CircleShape),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = "Tools",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
