package com.soll.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector

data class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

data class ToolDestination(
    val route: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

object Routes {
    const val CHAT = "chat"
    const val TASKS = "tasks"
    const val DEVICES = "devices"
    const val TOOLS = "tools"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    const val ACTIVITY_HISTORY = "activity_history"
    const val PORTABLE_SSD = "portable_ssd"
    const val MUSIC = "music"
    const val BOOK_READER = "book_reader"
    const val BREATHING = "guided_breathing"
    const val SCANNER = "scanner"
    const val DEVICE_QA = "device_qa"
}

object AppDestinations {
    val Chat = AppDestination(Routes.CHAT, "Чат", Icons.AutoMirrored.Filled.Message)
    val Tasks = AppDestination(Routes.TASKS, "Задачи", Icons.Default.TaskAlt)
    val Devices = AppDestination(Routes.DEVICES, "Гаджеты", Icons.Default.Devices)
    val Tools = AppDestination(Routes.TOOLS, "Утилиты", Icons.Default.Build)
    val Logs = AppDestination(Routes.LOGS, "Логи", Icons.Default.History)
    val Settings = AppDestination(Routes.SETTINGS, "Настройки", Icons.Default.Settings)

    val bottomBar = listOf(Chat, Tasks, Tools, Settings)

    val tools = listOf(
        ToolDestination(
            route = Routes.MUSIC,
            title = "Музыка",
            description = "Локальная медиатека, фон и управление с экрана блокировки",
            icon = Icons.Default.LibraryMusic,
        ),
        ToolDestination(
            route = Routes.BOOK_READER,
            title = "Читалка",
            description = "Чтение EPUB и озвучивание текста через TTS",
            icon = Icons.Default.Book,
        ),
        ToolDestination(
            route = Routes.BREATHING,
            title = "Дыхание",
            description = "Короткие дыхательные сессии и история практики",
            icon = Icons.Default.Air,
        ),
        ToolDestination(
            route = Devices.route,
            title = Devices.title,
            description = "Aquik, ESP, сенсоры, команды и логи",
            icon = Devices.icon,
        ),
        ToolDestination(
            route = Routes.PORTABLE_SSD,
            title = "SSD Wiki",
            description = "Чтение wiki, daily и задач с portable SSD через USB OTG",
            icon = Icons.Default.FolderOpen,
        ),
        ToolDestination(
            route = Routes.ACTIVITY_HISTORY,
            title = "Активность",
            description = "Экономный шагомер, геоистория и фоновый демон",
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
        ),
        ToolDestination(
            route = Routes.LOGS,
            title = "Логи",
            description = "История чата, уведомлений, задач и синхронизации",
            icon = Icons.Default.History,
        ),
    )
}
