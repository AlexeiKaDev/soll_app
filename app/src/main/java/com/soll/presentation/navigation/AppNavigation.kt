package com.soll.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soll.data.notification.AppForegroundState
import com.soll.presentation.screens.chat.ChatScreen
import com.soll.presentation.screens.devices.DevicesScreen
import com.soll.presentation.screens.logs.LogsScreen
import com.soll.presentation.screens.settings.DeviceQaScreen
import com.soll.presentation.screens.settings.SettingsScreen
import com.soll.presentation.screens.tasks.TaskBoardScreen
import com.soll.presentation.screens.tools.ToolsScreen
import com.soll.presentation.screens.tools.breathing.BreathingScreen
import com.soll.presentation.screens.tools.bookreader.BookReaderScreen
import com.soll.presentation.screens.tools.fieldmap.FieldMapScreen
import com.soll.presentation.screens.tools.music.MusicScreen
import com.soll.presentation.screens.tools.portablessd.PortableSsdScreen
import com.soll.presentation.screens.tools.scanner.ScannerScreen

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    launchCommand: AppLaunchCommand? = null,
    onLaunchCommandConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val screens = AppDestinations.bottomBar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var pendingLogsTab by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(currentRoute) {
        AppForegroundState.updateCurrentRoute(currentRoute)
    }

    LaunchedEffect(launchCommand?.nonce) {
        val command = launchCommand ?: return@LaunchedEffect
        when (command.section) {
            AppLaunchTargets.SECTION_CHAT -> {
                navController.navigate(AppDestinations.Chat.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
            AppLaunchTargets.SECTION_TASKS -> {
                navController.navigate(AppDestinations.Tasks.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
            AppLaunchTargets.SECTION_LOGS -> {
                pendingLogsTab = when (command.logsTab) {
                    AppLaunchTargets.LOGS_TAB_NOTIFICATIONS -> LOGS_TAB_NOTIFICATIONS
                    else -> null
                }
                navController.navigate(AppDestinations.Logs.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
            AppLaunchTargets.SECTION_PORTABLE_SSD -> {
                navController.navigate(Routes.PORTABLE_SSD) {
                    launchSingleTop = true
                }
            }
            AppLaunchTargets.SECTION_MUSIC -> {
                navController.navigate(Routes.MUSIC) {
                    launchSingleTop = true
                }
            }
            AppLaunchTargets.SECTION_BOOK_READER -> {
                navController.navigate(Routes.BOOK_READER) {
                    launchSingleTop = true
                }
            }
            AppLaunchTargets.SECTION_SETTINGS -> {
                navController.navigate(AppDestinations.Settings.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        onLaunchCommandConsumed()
    }

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
                                navController.navigateBottomBarRoute(screen.route)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestinations.Chat.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(AppDestinations.Chat.route) {
                ChatScreen(
                    onOpenSettings = {
                        navController.navigate(AppDestinations.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppDestinations.Tasks.route) {
                TaskBoardScreen()
            }
            composable(AppDestinations.Devices.route) {
                DevicesScreen()
            }
            composable(AppDestinations.Tools.route) {
                ToolsScreen(
                    onNavigateToDestination = { destination ->
                        navController.navigate(destination.route)
                    },
                )
            }
            composable(AppDestinations.Logs.route) {
                LogsScreen(
                    initialTab = pendingLogsTab,
                    onInitialTabConsumed = { pendingLogsTab = null },
                )
            }
            composable(AppDestinations.Settings.route) {
                SettingsScreen(
                    onOpenDeviceQa = {
                        navController.navigate(Routes.DEVICE_QA) {
                            launchSingleTop = true
                        }
                    },
                    onScanSollPairingQr = {
                        navController.navigate(Routes.SCANNER) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.DEVICE_QA) {
                DeviceQaScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PORTABLE_SSD) {
                PortableSsdScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.MUSIC) {
                MusicScreen(
                    onBack = { navController.popBackStack() }
                )
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
            composable(Routes.SCANNER) {
                ScannerScreen(
                    onBack = { navController.popBackStack() },
                    autoStartCamera = true,
                    pairingMode = true,
                )
            }
            composable(Routes.ACTIVITY_HISTORY) {
                FieldMapScreen(
                    onBack = { navController.popBackStack() },
                    initialActivityFocus = true,
                )
            }
        }
    }
}

private const val LOGS_TAB_NOTIFICATIONS = 3

private fun NavHostController.navigateBottomBarRoute(route: String) {
    if (currentDestination?.hierarchy?.any { it.route == route } == true) return
    if (route == AppDestinations.Chat.route && popBackStack(AppDestinations.Chat.route, false)) return

    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
