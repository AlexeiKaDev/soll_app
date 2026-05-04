package com.soll.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soll.presentation.screens.home.HomeScreen
import com.soll.presentation.screens.logs.LogsScreen
import com.soll.presentation.screens.settings.SettingsScreen
import com.soll.presentation.screens.tools.ToolsScreen
import com.soll.presentation.screens.tools.breathing.BreathingScreen
import com.soll.presentation.screens.tools.bookreader.BookReaderScreen
import com.soll.presentation.screens.tools.coursecoach.CourseCoachScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Tools : Screen("tools", "Tools", Icons.Default.Build)
    data object Logs : Screen("logs", "Logs", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

// Additional routes (not in bottom bar)
object Routes {
    const val BOOK_READER = "book_reader"
    const val BREATHING = "guided_breathing"
    const val COURSE_COACH = "course_coach"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Tools, Screen.Logs, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar on certain screens
    val showBottomBar = currentRoute in screens.map { it.route }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    val currentDestination = navBackStackEntry?.destination

                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen()
            }
            composable(Screen.Tools.route) {
                ToolsScreen(
                    onNavigateToCourseCoach = {
                        navController.navigate(Routes.COURSE_COACH)
                    },
                    onNavigateToBookReader = {
                        navController.navigate(Routes.BOOK_READER)
                    },
                    onNavigateToBreathing = {
                        navController.navigate(Routes.BREATHING)
                    },
                )
            }
            composable(Screen.Logs.route) {
                LogsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(Routes.BOOK_READER) {
                BookReaderScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.BREATHING) {
                BreathingScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.COURSE_COACH) {
                CourseCoachScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
