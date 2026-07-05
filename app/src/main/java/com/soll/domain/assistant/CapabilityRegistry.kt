package com.soll.domain.assistant

import android.Manifest
import android.annotation.SuppressLint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityRegistry @Inject constructor(
    private val settings: CapabilitySettings,
) {
    val capabilities: List<Capability> = CURRENT_COMMAND_CAPABILITIES

    private val byCommand: Map<String, Capability> =
        capabilities.associateBy { it.id.lowercase() }

    fun get(command: String): Capability? = byCommand[command.lowercase()]

    fun checkCommand(command: String): CapabilityDecision {
        val capability = get(command)
            ?: return CapabilityDecision(
                allowed = false,
                capability = null,
                reason = CapabilityBlockReason.NOT_REGISTERED,
                message = "Для /$command не зарегистрирован capability-контракт.",
            )

        if (capability.riskTier == RiskTier.BLOCKED) {
            return CapabilityDecision(
                allowed = false,
                capability = capability,
                reason = CapabilityBlockReason.BLOCKED_TIER,
                message = "${capability.name} всегда заблокирована политикой безопасности.",
            )
        }

        if (capability.riskTier.isRisky() && !settings.isRiskyCapabilitiesEnabled()) {
            return CapabilityDecision(
                allowed = false,
                capability = capability,
                reason = CapabilityBlockReason.RISKY_CAPABILITIES_DISABLED,
                message = "Рискованные возможности отключены в настройках.",
            )
        }

        if (!settings.isCapabilityEnabled(capability)) {
            return CapabilityDecision(
                allowed = false,
                capability = capability,
                reason = CapabilityBlockReason.CAPABILITY_DISABLED,
                message = "${capability.name} отключена в настройках.",
            )
        }

        return CapabilityDecision(
            allowed = true,
            capability = capability,
            message = "Разрешено",
        )
    }

    companion object {
        @SuppressLint("InlinedApi")
        val CURRENT_COMMAND_CAPABILITIES: List<Capability> = listOf(
            capability("chat", "Чат Soll", "Обмен сообщениями с сервером Soll и получение действий.", RiskTier.SAFE_INFO),
            capability("start", "Старт", "Стартовая справка Telegram-бота Soll.", RiskTier.SAFE_INFO),
            capability("help", "Помощь", "Список доступных команд и подсказки по использованию.", RiskTier.SAFE_INFO),
            capability("ping", "Проверка связи", "Быстрая проверка доступности Android-бота.", RiskTier.SAFE_INFO),
            capability("status", "Статус", "Чтение состояния приложения, сервера и устройства.", RiskTier.SAFE_INFO),
            capability("info", "Информация", "Чтение общей информации о приложении и устройстве.", RiskTier.SAFE_INFO),
            capability("logs", "Логи", "Чтение истории чата, уведомлений, задач и синхронизации.", RiskTier.SAFE_INFO),
            capability("storage", "Хранилище", "Чтение сводки по локальному хранилищу.", RiskTier.SAFE_INFO),
            capability("files", "Файлы", "Чтение списка локальных файлов.", RiskTier.FILE_MEDIA),
            capability("download", "Скачать файл", "Отправка локального файла через Telegram.", RiskTier.FILE_MEDIA),
            capability(
                id = "sms",
                name = "SMS",
                description = "Чтение SMS на Android-устройстве.",
                riskTier = RiskTier.COMMUNICATION,
                permissions = listOf(Manifest.permission.READ_SMS),
            ),
            capability(
                id = "sms_send",
                name = "Отправка SMS",
                description = "Отправка SMS с Android-устройства.",
                riskTier = RiskTier.COMMUNICATION,
                permissions = listOf(Manifest.permission.SEND_SMS),
            ),
            capability(
                id = "calls",
                name = "Журнал звонков",
                description = "Чтение журнала звонков Android.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(Manifest.permission.READ_CALL_LOG),
            ),
            capability(
                id = "call",
                name = "Телефонный звонок",
                description = "Запуск телефонного звонка с Android-устройства.",
                riskTier = RiskTier.COMMUNICATION,
                permissions = listOf(Manifest.permission.CALL_PHONE),
            ),
            capability(
                id = "contacts",
                name = "Контакты",
                description = "Чтение контактов Android.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(Manifest.permission.READ_CONTACTS),
            ),
            capability(
                id = "location",
                name = "Геолокация",
                description = "Получение текущей геопозиции Android-устройства.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            ),
            capability(
                id = "photo",
                name = "Фото",
                description = "Съемка фото с камеры Android-устройства.",
                riskTier = RiskTier.FILE_MEDIA,
                permissions = listOf(Manifest.permission.CAMERA),
            ),
            capability(
                id = "record",
                name = "Аудиозапись",
                description = "Запись аудио с микрофона Android-устройства.",
                riskTier = RiskTier.FILE_MEDIA,
                permissions = listOf(Manifest.permission.RECORD_AUDIO),
            ),
            capability("tasks", "Задачи", "Чтение и обновление задач Soll через сервер.", RiskTier.SAFE_INFO),
            capability("sync", "Синхронизация Soll", "Проверка сервера Soll и чтение доски задач.", RiskTier.SAFE_INFO),
            capability("jobs", "Задачи инструментов", "Чтение статуса локальных задач инструментов.", RiskTier.SAFE_INFO),
            capability("raw", "Сырая заметка", "Сохранение сырой заметки в локальную базу Soll.", RiskTier.SAFE_INFO),
            capability(
                id = "server_action",
                name = "Действия сервера",
                description = "Выполнение явно разрешенных действий, пришедших из чата Soll.",
                riskTier = RiskTier.MONEY_OR_EXTERNAL_ACTION,
                requiresConfirmation = true,
            ),
            capability(
                id = "devices",
                name = "Гаджеты",
                description = "Подключение к умным гаджетам, внешним ESP-устройствам и чтение их телеметрии.",
                riskTier = RiskTier.DUAL_USE_HARDWARE,
                requiresConfirmation = false,
                enabledByDefault = false,
            ),
            capability(
                id = "field_map",
                name = "Активность",
                description = "Локальный трекер шагов и геоистория для личной активности.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                requiresConfirmation = false,
            ),
            capability(
                id = "portable_ssd",
                name = "SSD Wiki",
                description = "Read-only доступ к wiki, daily и задачам на portable SSD.",
                riskTier = RiskTier.SAFE_INFO,
            ),
            capability(
                id = "notify",
                name = "Уведомления",
                description = "Показ локальных push-уведомлений Soll.",
                riskTier = RiskTier.DEVICE_CONTROL,
            ),
            capability(
                id = "vibrate",
                name = "Вибрация",
                description = "Короткий локальный сигнал вибрацией.",
                riskTier = RiskTier.DEVICE_CONTROL,
                permissions = listOf(Manifest.permission.VIBRATE),
            ),
            capability(
                id = "flashlight",
                name = "Фонарик",
                description = "Управление фонариком Android-устройства.",
                riskTier = RiskTier.DEVICE_CONTROL,
                permissions = listOf(Manifest.permission.CAMERA),
            ),
            capability("volume", "Громкость", "Управление громкостью Android-устройства.", RiskTier.DEVICE_CONTROL),
            capability("alarm", "Будильник", "Создание локального будильника Android.", RiskTier.DEVICE_CONTROL),
            capability(
                id = "brightness",
                name = "Яркость",
                description = "Изменение яркости экрана Android.",
                riskTier = RiskTier.DEVICE_CONTROL,
                permissions = listOf(Manifest.permission.WRITE_SETTINGS),
            ),
            capability(
                id = "bluetooth",
                name = "Bluetooth",
                description = "Чтение или управление Bluetooth-состоянием Android.",
                riskTier = RiskTier.DEVICE_CONTROL,
                permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
            ),
            capability(
                id = "wifi",
                name = "Wi-Fi",
                description = "Чтение или управление Wi-Fi-состоянием Android.",
                riskTier = RiskTier.DEVICE_CONTROL,
                permissions = listOf(Manifest.permission.ACCESS_WIFI_STATE),
            ),
        )

        private fun capability(
            id: String,
            name: String,
            description: String,
            riskTier: RiskTier,
            permissions: List<String> = emptyList(),
            requiresConfirmation: Boolean = riskTier.requiresConfirmationByDefault(),
            enabledByDefault: Boolean = riskTier != RiskTier.BLOCKED,
        ): Capability = Capability(
            id = id,
            name = name,
            description = description,
            riskTier = riskTier,
            requiredAndroidPermissions = permissions,
            requiresConfirmation = requiresConfirmation,
            enabledByDefault = enabledByDefault,
            auditRequired = riskTier.isRisky(),
        )
    }
}

fun RiskTier.isRisky(): Boolean = this != RiskTier.SAFE_INFO

private fun RiskTier.requiresConfirmationByDefault(): Boolean = when (this) {
    RiskTier.PERSONAL_DATA,
    RiskTier.COMMUNICATION,
    RiskTier.FILE_MEDIA,
    RiskTier.MONEY_OR_EXTERNAL_ACTION,
    RiskTier.DUAL_USE_HARDWARE -> true
    RiskTier.SAFE_INFO,
    RiskTier.DEVICE_CONTROL,
    RiskTier.BLOCKED -> false
}
