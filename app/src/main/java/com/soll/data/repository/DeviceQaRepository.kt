package com.soll.data.repository

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.soll.data.notification.SollNotificationChannels
import com.soll.domain.deviceqa.DeviceQaCategory
import com.soll.domain.deviceqa.DeviceQaCheck
import com.soll.domain.deviceqa.DeviceQaCheckId
import com.soll.domain.deviceqa.DeviceQaManualResult
import com.soll.domain.deviceqa.DeviceQaReportFormatter
import com.soll.domain.deviceqa.DeviceQaStatus
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
        return listOf(
            notificationPermissionCheck(),
            notificationChannelsCheck(),
            notificationAndroid13FlowCheck(),
            notificationTapRoutingCheck(),
            batteryOptimizationCheck(),
            themeVisualCheck(),
            gadgetProtocolSchemaCheck(),
            gadgetServerLocalBindingCheck(),
            gadgetMeshOutboxWorkerCheck(),
            gadgetReadOnlyCommandWorkerCheck(),
            gadgetManualWriteFlowCheck(),
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
        val disabled = activeNotificationChannels.filter { channel ->
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

    private val activeNotificationChannels = listOf(
        SollNotificationChannel.CHAT,
        SollNotificationChannel.ACTIVITY_TRACKING,
        SollNotificationChannel.TTS_PLAYBACK,
        SollNotificationChannel.MUSIC_PLAYBACK,
        SollNotificationChannel.SERVER_SYNC,
        SollNotificationChannel.EVENTS,
        SollNotificationChannel.ALERTS,
        SollNotificationChannel.TOOL_JOBS,
    )

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
                "Android может задерживать чат, sync worker, активность и уведомления. Для Soll лучше отключить оптимизацию."
            },
            status = if (ignored) DeviceQaStatus.OK else DeviceQaStatus.WARNING,
            manual = false,
            actionLabel = "Открыть батарею",
        )
    }

    private fun themeVisualCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.THEME_VISUAL_PASS,
            category = DeviceQaCategory.THEME,
            title = "Палитры и контраст",
            detail = "Переключи Классика, Аврора и Aquik. Проверь Чат, Задачи, Утилиты, Гаджеты, SSD Wiki, Активность, Логи и Настройки.",
            status = DeviceQaStatus.NEEDS_MANUAL_TEST,
            manual = true,
            expectedResult = "Во всех трех темах текст контрастный, активные элементы различимы, карточки не сливаются с фоном, длинные подписи не ломают строки.",
            roadmapRef = "Theme / Device QA: palette visual pass",
        )

    private fun gadgetProtocolSchemaCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.GADGET_PROTOCOL_SCHEMA,
            category = DeviceQaCategory.GADGETS,
            title = "Контракт сервера",
            detail = if (settingsRepository.sollServerUrl.isBlank()) {
                "URL сервера Soll не задан. Без него Android не сможет сверить /api/v1/protocol/schema."
            } else {
                "Открой Гаджеты -> Сервер Soll и нажми «Контракт», чтобы сверить Android с текущим protocol/discovery schema и worker contracts."
            },
            status = if (settingsRepository.sollServerUrl.isBlank()) {
                DeviceQaStatus.WARNING
            } else {
                DeviceQaStatus.NEEDS_MANUAL_TEST
            },
            manual = true,
            expectedResult = "Проверка контракта показывает совместимость soll-protocol-v1, soll-gadget-discovery-v1, token_refresh и worker contracts без предупреждений.",
            roadmapRef = "ESP Connector / Device QA: Soll server protocol schema compatibility",
            actionLabel = "Обновить",
        )

    private fun gadgetServerLocalBindingCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.GADGET_SERVER_LOCAL_BINDING,
            category = DeviceQaCategory.GADGETS,
            title = "Связь server gadget и телефона",
            detail = if (settingsRepository.sollServerUrl.isBlank()) {
                "URL сервера Soll не задан. Binding server gadget -> local KnownDevice нельзя проверить."
            } else {
                "В Гаджеты -> Сервер Soll обнови snapshots и проверь, что нужный ESP/Aquik имеет exact id или heartbeat endpoint/local IP, совпадающий с локальным устройством. Ambiguous/no-local binding должен блокировать исполнение команд."
            },
            status = if (settingsRepository.sollServerUrl.isBlank()) {
                DeviceQaStatus.WARNING
            } else {
                DeviceQaStatus.NEEDS_MANUAL_TEST
            },
            manual = true,
            expectedResult = "Нужный server gadget однозначно связан с локальным KnownDevice по exact id или endpoint/local IP; неоднозначные совпадения не используются для команд.",
            roadmapRef = "ESP Connector / Device QA: server-local gadget binding before write-capable commands",
            actionLabel = "Обновить",
        )

    private fun gadgetMeshOutboxWorkerCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.GADGET_MESH_OUTBOX_WORKER,
            category = DeviceQaCategory.GADGETS,
            title = "Mesh/outbox worker",
            detail = if (settingsRepository.sollServerUrl.isBlank()) {
                "URL сервера Soll не задан. Нельзя проверить claim/ACK/retry для mesh outbox."
            } else {
                "В Гаджеты -> Сервер Soll проверь mesh counters и recent outbox: allowlist payload должен пройти claim -> ACK, unsupported/command payload должен получить failed attempt без исполнения."
            },
            status = if (settingsRepository.sollServerUrl.isBlank()) {
                DeviceQaStatus.WARNING
            } else {
                DeviceQaStatus.NEEDS_MANUAL_TEST
            },
            manual = true,
            expectedResult = "Worker claim-ит один outbox item за sync run, ACK-ает только status/brief/note/task payload и отправляет failed attempt для command/unknown без произвольных действий.",
            roadmapRef = "ESP Connector / Device QA: mesh/outbox worker claim, ACK and retry",
            actionLabel = "Обновить",
        )

    private fun gadgetReadOnlyCommandWorkerCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.GADGET_READ_ONLY_COMMAND_WORKER,
            category = DeviceQaCategory.GADGETS,
            title = "Read-only команды сервера",
            detail = if (settingsRepository.sollServerUrl.isBlank()) {
                "URL сервера Soll не задан. Нельзя проверить lifecycle server gadget command."
            } else {
                "Для связанного ESP/Aquik отправь read-only команду сервера (`getSensors`, `getActuators` или `getSystemInfo`) и проверь claim -> ack -> result в истории."
            },
            status = if (settingsRepository.sollServerUrl.isBlank()) {
                DeviceQaStatus.WARNING
            } else {
                DeviceQaStatus.NEEDS_MANUAL_TEST
            },
            manual = true,
            expectedResult = "Android выполняет только read-only команды через локальный WebSocket, пишет локальный audit event и закрывает server command как done/failed с result payload.",
            roadmapRef = "ESP Connector / Device QA: read-only gadget command worker",
            actionLabel = "Обновить",
        )

    private fun gadgetManualWriteFlowCheck(): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.GADGET_MANUAL_WRITE_FLOW,
            category = DeviceQaCategory.GADGETS,
            title = "Manual write flow",
            detail = if (settingsRepository.sollServerUrl.isBlank()) {
                "URL сервера Soll не задан. Manual write flow нельзя проверить без server approvals и command history."
            } else {
                "После approval переведи write-команду в `manual_ready`, нажми «Вручную» только для известного локального ESP/Aquik и проверь `manual-result` в server history."
            },
            status = if (settingsRepository.sollServerUrl.isBlank()) {
                DeviceQaStatus.WARNING
            } else {
                DeviceQaStatus.NEEDS_MANUAL_TEST
            },
            manual = true,
            expectedResult = "`manual_ready` write-команда не исполняется фоном, требует явного UI-подтверждения, имеет однозначный local binding и закрывается на сервере как done/failed через manual-result.",
            roadmapRef = "ESP Connector / Device QA: explicit manual write execution on real ESP/Aquik",
            actionLabel = "Обновить",
        )

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

    private companion object {
        const val DEVICE_QA_NOTIFICATION_ID = 2301
    }
}
