package com.soll.presentation.navigation

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
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
import com.soll.presentation.screens.share.ShareImportScreen
import com.soll.presentation.screens.tasks.TaskBoardScreen
import com.soll.presentation.screens.today.TodayScreen
import com.soll.presentation.screens.todo.DailyTodoActivity
import com.soll.presentation.screens.tools.ToolsScreen
import com.soll.presentation.screens.voice.VoiceScreen
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
    val context = LocalContext.current
    val screens = AppDestinations.bottomBar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var pendingLogsTab by remember { mutableStateOf<Int?>(null) }
    var pendingSharedLink by remember { mutableStateOf<SharedLinkPayload?>(null) }

    LaunchedEffect(currentRoute) {
        AppForegroundState.updateCurrentRoute(currentRoute)
    }

    LaunchedEffect(launchCommand?.nonce) {
        val command = launchCommand ?: return@LaunchedEffect
        when (command.section) {
            AppLaunchTargets.SECTION_TODAY -> {
                navController.navigate(AppDestinations.Today.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                }
            }
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
            AppLaunchTargets.SECTION_SHARE_IMPORT -> {
                pendingSharedLink = command.sharedLink
                navController.navigate(Routes.SHARE_IMPORT) {
                    launchSingleTop = true
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
            startDestination = AppDestinations.Today.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(AppDestinations.Today.route) {
                TodayScreen()
            }
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
                        if (destination.route == Routes.DAILY_TODO) {
                            context.startActivity(Intent(context, DailyTodoActivity::class.java))
                        } else {
                            navController.navigate(destination.route)
                        }
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
            composable(Routes.VOICE) {
                VoiceScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SCANNER) {
                ScannerScreen(
                    onBack = { navController.popBackStack() },
                    onPairingCompleted = {
                        navController.navigate(AppDestinations.Settings.route) {
                            popUpTo(AppDestinations.Settings.route) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
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
            composable(Routes.SHARE_IMPORT) {
                ShareImportScreen(
                    payload = pendingSharedLink,
                    onBack = {
                        pendingSharedLink = null
                        if (!navController.popBackStack()) {
                            navController.navigateBottomBarRoute(AppDestinations.Today.route)
                        }
                    },
                    onOpenToday = {
                        pendingSharedLink = null
                        navController.navigateBottomBarRoute(AppDestinations.Today.route)
                    },
                )
            }
        }
    }
}

private const val LOGS_TAB_NOTIFICATIONS = 3

private fun NavHostController.navigateBottomBarRoute(route: String) {
    if (currentDestination?.hierarchy?.any { it.route == route } == true) return
    if (route == AppDestinations.Today.route && popBackStack(AppDestinations.Today.route, false)) return

    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
