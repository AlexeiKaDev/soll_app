package com.soll.data.repository

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.soll.data.notification.SollNotificationChannels
import com.soll.data.service.MusicPlaybackService
import com.soll.domain.deviceqa.DeviceQaCategory
import com.soll.domain.deviceqa.DeviceQaCheck
import com.soll.domain.deviceqa.DeviceQaCheckId
import com.soll.domain.deviceqa.DeviceQaManualResult
import com.soll.domain.deviceqa.DeviceQaReportFormatter
import com.soll.domain.deviceqa.DeviceQaStatus
import com.soll.domain.music.MusicPlayerState
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceQaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val notificationCenter: SollNotificationCenter,
) {
    fun checks(): List<DeviceQaCheck> {
        notificationCenter.ensureChannels()
        val musicState = MusicPlaybackService.currentState()
        return listOf(
            notificationPermissionCheck(),
            notificationChannelsCheck(),
            notificationAndroid13FlowCheck(),
            notificationTapRoutingCheck(),
            notificationMediaSessionCheck(musicState),
            batteryOptimizationCheck(),
            DeviceQaCheck(
                id = DeviceQaCheckId.MUSIC_SCREEN_OFF,
                category = DeviceQaCategory.MUSIC,
                title = "Музыка с выключенным экраном",
                detail = if (musicState.isServiceActive) {
                    "Сервис музыки активен. На телефоне нужно проверить 10+ минут воспроизведения с выключенным экраном."
                } else {
                    "Запусти музыку и отметь результат после проверки с выключенным экраном."
                },
                status = DeviceQaStatus.NEEDS_MANUAL_TEST,
                manual = true,
                expectedResult = "Музыка играет 10+ минут при выключенном экране, сервис не выгружается, после разблокировки позиция трека актуальна.",
                roadmapRef = "Music Player / Device QA: screen-off playback",
                actionLabel = "Открыть батарею",
            ),
            DeviceQaCheck(
                id = DeviceQaCheckId.MUSIC_LOCKSCREEN_CONTROLS,
                category = DeviceQaCategory.MUSIC,
                title = "Управление музыкой в уведомлении",
                detail = "Проверь play/pause/next на экране блокировки и в шторке уведомлений.",
                status = DeviceQaStatus.NEEDS_MANUAL_TEST,
                manual = true,
                expectedResult = "В шторке и на экране блокировки есть одно медиа-уведомление с рабочими play/pause/next/stop без дубликатов.",
                roadmapRef = "Music Player / Device QA: notification, lockscreen and headset controls",
                actionLabel = "Тест уведомления",
            ),
            DeviceQaCheck(
                id = DeviceQaCheckId.MUSIC_AUDIO_FOCUS,
                category = DeviceQaCategory.MUSIC,
                title = "Звонки и аудиофокус",
                detail = "Проверь, что звонок или другое аудио корректно приглушает/ставит музыку на паузу средствами Android.",
                status = DeviceQaStatus.NEEDS_MANUAL_TEST,
                manual = true,
                expectedResult = "При звонке или конкурирующем аудио Android audio focus ставит музыку на паузу или приглушает ее, затем корректно восстанавливает состояние.",
                roadmapRef = "Music Player / Device QA: Android audio focus behavior",
            ),
            widgetLauncherColdCheck(),
            widgetMediaControlsCheck(),
            themeVisualCheck(),
            nfcOwnedTagsCheck(),
            nfcAccessDiagnosticCheck(),
        ).map { check ->
            check.copy(lastManualResult = settingsRepository.getDeviceQaManualResult(check.id))
        }
    }

    suspend fun postTestNotification() {
        notificationCenter.post(
            SollNotificationRequest(
                channel = SollNotificationChannel.ALERTS,
                type = "device_qa_test",
                source = "settings_device_qa",
                title = "Проверка Soll",
                message = "Если это уведомление видно и по нажатию открываются логи, канал работает.",
                priority = SollNotificationPriority.HIGH,
                systemNotificationId = DEVICE_QA_NOTIFICATION_ID,
            )
        )
    }

    fun recordManualResult(id: DeviceQaCheckId, passed: Boolean) {
        settingsRepository.setDeviceQaManualResult(
            id = id,
            result = DeviceQaManualResult(
                status = if (passed) DeviceQaStatus.MANUAL_OK else DeviceQaStatus.MANUAL_PROBLEM,
                checkedAt = System.currentTimeMillis(),
                deviceSummary = currentDeviceSummary(),
            ),
        )
    }

    fun clearManualResult(id: DeviceQaCheckId) {
        settingsRepository.clearDeviceQaManualResult(id)
    }

    fun buildReport(): String =
        DeviceQaReportFormatter.buildReport(
            checks = checks(),
            generatedAt = System.currentTimeMillis(),
            deviceSummary = currentDeviceSummary(),
            appSummary = currentAppSummary(),
        )

    private fun notificationPermissionCheck(): DeviceQaCheck {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val ok = enabled && permissionGranted
        return DeviceQaCheck(
            id = DeviceQaCheckId.NOTIFICATION_PERMISSION,
            category = DeviceQaCategory.NOTIFICATIONS,
            title = "Разрешение уведомлений",
            detail = if (ok) {
                "Android разрешает системные уведомления Soll."
            } else {
                "Разреши уведомления в настройках Android, иначе тестовые события и алерты не появятся."
            },
            status = if (ok) DeviceQaStatus.OK else DeviceQaStatus.PROBLEM,
            manual = false,
            actionLabel = "Открыть уведомления",
        )
    }

    private fun notificationChannelsCheck(): DeviceQaCheck {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val disabled = SollNotificationChannel.entries.filter { channel ->
            notificationManager.getNotificationChannel(channel.channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        }
        return DeviceQaCheck(
            id = DeviceQaCheckId.NOTIFICATION_CHANNELS,
            category = DeviceQaCategory.NOTIFICATIONS,
            title = "Каналы Soll",
            detail = if (disabled.isEmpty()) {
                "Каналы созданы и не отключены пользователем."
            } else {
                "Отключены каналы: ${disabled.joinToString { it.channelId }}."
            },
            status = if (disabled.isEmpty()) DeviceQaStatus.OK else DeviceQaStatus.WARNING,
            manual = false,
            actionLabel = "Открыть уведомления",
        )
    }

    private fun notificationAndroid13FlowCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.NOTIFICATION_ANDROID13_FLOW,
            category = DeviceQaCategory.NOTIFICATIONS,
            title = "Android 13+ и каналы",
            detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "Проверь первый запрос разрешения, отключение/включение каналов Soll в Android и возврат статуса после обновления."
            } else {
                "На этом Android нет runtime-разрешения POST_NOTIFICATIONS, но каналы/общий тумблер уведомлений все равно стоит проверить вручную."
            },
            status = DeviceQaStatus.NEEDS_MANUAL_TEST,
            manual = true,
            expectedResult = "На Android 13+ первый запуск запрашивает POST_NOTIFICATIONS, отключение каналов отражается в статусе, повторное включение возвращает ОК.",
            roadmapRef = "Notifications / Device QA: Android 13+ permission flow and channel toggles",
            actionLabel = "Открыть уведомления",
        )

    private fun notificationTapRoutingCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.NOTIFICATION_TAP_ROUTING,
            category = DeviceQaCategory.NOTIFICATIONS,
            title = "Переход из уведомления",
            detail = "Нажми «Тест», затем тапни системное уведомление: приложение должно открыться в Логи -> Увед.",
            status = DeviceQaStatus.NEEDS_MANUAL_TEST,
            manual = true,
            expectedResult = "Тап по тестовому уведомлению открывает Soll сразу в Логи -> Увед., а не только главный экран.",
            roadmapRef = "Notifications / Device QA: notification tap opens Soll",
            actionLabel = "Тест",
        )

    private fun notificationMediaSessionCheck(musicState: MusicPlayerState): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.NOTIFICATION_MEDIA_SESSION,
            category = DeviceQaCategory.NOTIFICATIONS,
            title = "Медиа-уведомление Media3",
            detail = if (musicState.isServiceActive) {
                "Музыка активна. Проверь, что медиа-уведомление и lockscreen-кнопки управляются Media3: play/pause/next/stop без дубликатов."
            } else {
                "Запусти музыку и проверь, что в шторке появляется одно медиа-уведомление Media3 с рабочими кнопками."
            },
            status = DeviceQaStatus.NEEDS_MANUAL_TEST,
            manual = true,
            expectedResult = "Медиа-уведомление принадлежит Media3 session, кнопки управляют текущим playback state и не конфликтуют с обычными уведомлениями Soll.",
            roadmapRef = "Notifications / Device QA: Media3 media notification ownership",
            actionLabel = "Открыть уведомления",
        )

    private fun batteryOptimizationCheck(): DeviceQaCheck {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        return DeviceQaCheck(
            id = DeviceQaCheckId.BATTERY_OPTIMIZATION,
            category = DeviceQaCategory.BACKGROUND,
            title = "Фоновая работа",
            detail = if (ignored) {
                "Оптимизация батареи отключена для Soll."
            } else {
                "Android может остановить фоновые сервисы. Для музыки, бота и синхронизации лучше отключить оптимизацию."
            },
            status = if (ignored) DeviceQaStatus.OK else DeviceQaStatus.WARNING,
            manual = false,
            actionLabel = "Открыть батарею",
        )
    }

    private fun widgetLauncherColdCheck(): DeviceQaCheck {
        val installed = installedWidgetCounts()
        val total = installed.total
        return DeviceQaCheck(
            id = DeviceQaCheckId.WIDGET_LAUNCHER_COLD,
            category = DeviceQaCategory.WIDGETS,
            title = "Виджеты на рабочем столе",
            detail = if (total > 0) {
                "Добавлено: музыка ${installed.music}, читалка ${installed.reader}, заметки ${installed.notes}. Перезапусти приложение/убей процесс и проверь открытие виджетов холодным стартом."
            } else {
                "Добавь виджеты Музыка, Читалка и Заметки на рабочий стол, затем отметь результат после проверки холодного запуска."
            },
            status = if (total > 0) DeviceQaStatus.NEEDS_MANUAL_TEST else DeviceQaStatus.WARNING,
            manual = true,
            expectedResult = "Виджеты Музыка, Читалка и Заметки открывают нужные инструменты/действия даже после холодного старта процесса.",
            roadmapRef = "Widgets / Device QA: launcher widgets and cold-start taps",
            actionLabel = "Обновить",
        )
    }

    private fun widgetMediaControlsCheck(): DeviceQaCheck {
        val installed = installedWidgetCounts()
        val hasMediaWidgets = installed.music > 0 || installed.reader > 0
        return DeviceQaCheck(
            id = DeviceQaCheckId.WIDGET_MEDIA_CONTROLS,
            category = DeviceQaCategory.WIDGETS,
            title = "Кнопки виджетов медиа",
            detail = if (hasMediaWidgets) {
                "Проверь play/pause/next/previous/stop в виджетах музыки и читалки вместе с уведомлением и экраном блокировки."
            } else {
                "Для этой проверки нужен хотя бы один виджет музыки или читалки на рабочем столе."
            },
            status = if (hasMediaWidgets) DeviceQaStatus.NEEDS_MANUAL_TEST else DeviceQaStatus.WARNING,
            manual = true,
            expectedResult = "Кнопки виджетов музыки и читалки синхронны с уведомлением, lockscreen controls и реальным состоянием плеера/TTS.",
            roadmapRef = "Widgets / Device QA: music and reader widgets update after media controls",
            actionLabel = "Обновить",
        )
    }

    private fun themeVisualCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.THEME_VISUAL_PASS,
            category = DeviceQaCategory.THEME,
            title = "Палитры и контраст",
            detail = "Переключи Классика, Аврора и Aquik. Проверь Home, Инструменты, Музыку, Читалку, Заметки и Настройки на читаемость без серых/сливающихся блоков.",
            status = DeviceQaStatus.NEEDS_MANUAL_TEST,
            manual = true,
            expectedResult = "Во всех трех темах текст контрастный, активные элементы различимы, карточки не сливаются с фоном, длинные подписи не ломают строки.",
            roadmapRef = "Theme / Device QA: palette visual pass",
        )

    private fun nfcOwnedTagsCheck(): DeviceQaCheck {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return DeviceQaCheck(
            id = DeviceQaCheckId.NFC_OWNED_TAGS,
            category = DeviceQaCategory.NFC,
            title = "Свои NFC-метки",
            detail = when {
                adapter == null -> "В телефоне нет NFC-модуля."
                !adapter.isEnabled -> "NFC выключен. Включи его перед тестом чтения/записи owned tags."
                else -> "NFC включен. Проверь чтение/запись только на своей NFC Forum Type 2/4 метке."
            },
            status = when {
                adapter == null -> DeviceQaStatus.PROBLEM
                !adapter.isEnabled -> DeviceQaStatus.WARNING
                else -> DeviceQaStatus.NEEDS_MANUAL_TEST
            },
            manual = adapter != null,
            expectedResult = "Своя NFC Forum Type 2/4 метка читается, NDEF-запись проходит, пустая NDEF-formatable метка форматируется перед записью.",
            roadmapRef = "NFC Tools / Device QA: owned NFC Forum Type 2/4 tags",
            actionLabel = "Открыть NFC",
        )
    }

    private fun nfcAccessDiagnosticCheck(): DeviceQaCheck {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return DeviceQaCheck(
            id = DeviceQaCheckId.NFC_ACCESS_FOB_DIAGNOSTIC,
            category = DeviceQaCategory.NFC,
            title = "Диагностика ключа подъезда",
            detail = when {
                adapter == null -> "NFC недоступен на устройстве."
                !adapter.isEnabled -> "NFC выключен. Можно только диагностировать HF/NFC-метки, без клонирования."
                else -> "Можно проверить, видит ли телефон метку и какой это тип. Клонирование/эмуляцию подъездного ключа не делаем."
            },
            status = if (adapter == null) DeviceQaStatus.PROBLEM else DeviceQaStatus.NEEDS_MANUAL_TEST,
            manual = adapter != null,
            expectedResult = "Телефон показывает тип доступной HF/NFC-метки или честно не видит LF 125 кГц ключ; клонирование и эмуляция не выполняются.",
            roadmapRef = "NFC Tools / Device QA: apartment fob diagnostic without cloning",
            actionLabel = "Открыть NFC",
        )
    }

    private fun installedWidgetCounts(): InstalledWidgetCounts {
        val manager = AppWidgetManager.getInstance(context)
        return InstalledWidgetCounts(
            music = manager.getAppWidgetIds(componentName(".presentation.widgets.MusicWidgetProvider")).size,
            reader = manager.getAppWidgetIds(componentName(".presentation.widgets.ReaderWidgetProvider")).size,
            notes = manager.getAppWidgetIds(componentName(".presentation.widgets.NotesWidgetProvider")).size,
        )
    }

    private fun componentName(relativeClassName: String): ComponentName =
        ComponentName(context.packageName, context.packageName + relativeClassName)

    private fun currentDeviceSummary(): String {
        val manufacturer = Build.MANUFACTURER.trim().takeIf { it.isNotBlank() }
        val model = Build.MODEL.trim().takeIf { it.isNotBlank() }
        val deviceName = listOfNotNull(manufacturer, model)
            .distinctBy { it.lowercase() }
            .joinToString(" ")
            .ifBlank { "Android-устройство" }
        return "$deviceName, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    private fun currentAppSummary(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "unknown"
        return "${context.packageName} $versionName ($versionCode)"
    }

    private data class InstalledWidgetCounts(
        val music: Int,
        val reader: Int,
        val notes: Int,
    ) {
        val total: Int = music + reader + notes
    }

    private companion object {
        const val DEVICE_QA_NOTIFICATION_ID = 2301
    }
}
