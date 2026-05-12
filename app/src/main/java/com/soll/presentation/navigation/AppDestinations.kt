package com.soll.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
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
    const val HOME = "home"
    const val TASKS = "tasks"
    const val DEVICES = "devices"
    const val TOOLS = "tools"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    const val BOOK_READER = "book_reader"
    const val BREATHING = "guided_breathing"
    const val RAW_NOTE = "raw_note"
    const val MUSIC = "music"
    const val SCANNER = "scanner"
    const val NFC = "nfc_tools"
    const val FIELD_MAP = "field_map"
    const val VOICE = "voice"
    const val ASK_SOLL = "ask_soll"
}

object AppDestinations {
    val Home = AppDestination(Routes.HOME, "Главная", Icons.Default.Home)
    val Tasks = AppDestination(Routes.TASKS, "Задачи", Icons.Default.TaskAlt)
    val Devices = AppDestination(Routes.DEVICES, "Гаджеты", Icons.Default.Devices)
    val Tools = AppDestination(Routes.TOOLS, "Утилиты", Icons.Default.Build)
    val Logs = AppDestination(Routes.LOGS, "Логи", Icons.Default.History)
    val Settings = AppDestination(Routes.SETTINGS, "Настройки", Icons.Default.Settings)

    val bottomBar = listOf(Home, Tasks, Tools, Logs, Settings)

    val tools = listOf(
        ToolDestination(
            route = Routes.ASK_SOLL,
            title = "Спросить",
            description = "Вопрос к серверу Soll без автодействий",
            icon = Icons.Default.AutoAwesome,
        ),
        ToolDestination(
            route = Routes.MUSIC,
            title = "Музыка",
            description = "Локальная медиатека, фон и управление с экрана блокировки",
            icon = Icons.Default.LibraryMusic,
        ),
        ToolDestination(
            route = Routes.VOICE,
            title = "Голос",
            description = "Голосовые команды по нажатию для Soll",
            icon = Icons.Default.Mic,
        ),
        ToolDestination(
            route = Devices.route,
            title = Devices.title,
            description = "Aquik, ESP, сенсоры, команды и логи",
            icon = Devices.icon,
        ),
        ToolDestination(
            route = Routes.RAW_NOTE,
            title = "Заметки",
            description = "Локальные заметки, теги и отправка в Soll",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
        ),
        ToolDestination(
            route = Routes.SCANNER,
            title = "Сканер",
            description = "EAN, QR, история дублей и экспорт в заметки",
            icon = Icons.Default.QrCodeScanner,
        ),
        ToolDestination(
            route = Routes.FIELD_MAP,
            title = "Карта",
            description = "Офлайн-точки, GPS, маршрут и экспорт в заметки",
            icon = Icons.Default.Map,
        ),
        ToolDestination(
            route = Routes.NFC,
            title = "NFC",
            description = "Диагностика меток, NDEF и проверка мобильного доступа",
            icon = Icons.Default.Nfc,
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
            description = "3 раунда дыхания: дыхание, задержка, восстановление",
            icon = Icons.Default.Air,
        ),
    )
}
