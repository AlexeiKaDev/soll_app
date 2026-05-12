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
            capability("start", "Старт", "Показ приветствия и вводной информации бота.", RiskTier.SAFE_INFO),
            capability("help", "Справка", "Показ доступных Telegram-команд.", RiskTier.SAFE_INFO),
            capability("ping", "Пинг", "Проверка ответа бота.", RiskTier.SAFE_INFO),
            capability("status", "Статус", "Чтение статуса устройства, батареи, памяти и сети.", RiskTier.SAFE_INFO),
            capability("info", "Информация об устройстве", "Чтение базовых данных устройства и Android.", RiskTier.SAFE_INFO),
            capability("logs", "Логи команд", "Чтение последних логов команд бота.", RiskTier.SAFE_INFO),
            capability("jobs", "Задачи инструментов", "Просмотр последних задач инструментов и их статусов.", RiskTier.SAFE_INFO),
            capability("sync", "Синхронизация Soll", "Проверка сервера Soll и чтение доски задач.", RiskTier.SAFE_INFO),
            capability("storage", "Хранилище", "Чтение объема и свободного места хранилища.", RiskTier.SAFE_INFO),
            capability(
                id = "ask_soll",
                name = "Спросить Soll",
                description = "Отправка явного вопроса на сервер Soll с безопасным контекстом и локальным аудитом ответа.",
                riskTier = RiskTier.PERSONAL_DATA,
                requiresConfirmation = false,
            ),
            capability(
                id = "raw",
                name = "Raw-заметка Soll",
                description = "Создание черновой заметки на сервере Soll.",
                riskTier = RiskTier.MONEY_OR_EXTERNAL_ACTION,
                requiresConfirmation = false,
            ),
            capability(
                id = "scanner",
                name = "Сканер",
                description = "Сканирование EAN/QR-кодов камерой и ручной ввод кодов для локальной истории.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(Manifest.permission.CAMERA),
                requiresConfirmation = false,
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
                id = "nfc",
                name = "NFC-инструменты",
                description = "Диагностика NFC/NDEF-меток и совместимости с официальным мобильным доступом без копирования ключей.",
                riskTier = RiskTier.DUAL_USE_HARDWARE,
                permissions = listOf(Manifest.permission.NFC),
                requiresConfirmation = false,
                enabledByDefault = false,
            ),
            capability(
                id = "music",
                name = "Музыка",
                description = "Импорт локальных аудиофайлов и фоновое воспроизведение музыки.",
                riskTier = RiskTier.FILE_MEDIA,
                requiresConfirmation = false,
            ),
            capability(
                id = "field_map",
                name = "Карта и поле",
                description = "Локальное сохранение геоточек, импорт координат из задач и открытие маршрута во внешней карте.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                requiresConfirmation = false,
            ),

            capability(
                id = "files",
                name = "Файлы",
                description = "Просмотр списка локальных файлов и папок.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                ),
            ),
            capability(
                id = "download",
                name = "Отправка файла",
                description = "Загрузка локального файла в Telegram.",
                riskTier = RiskTier.FILE_MEDIA,
                permissions = listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                ),
                requiresConfirmation = true,
            ),

            capability(
                id = "sms",
                name = "Чтение SMS",
                description = "Чтение последних SMS-сообщений.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(Manifest.permission.READ_SMS),
                requiresConfirmation = true,
            ),
            capability(
                id = "sms_send",
                name = "Отправка SMS",
                description = "Отправка SMS-сообщения.",
                riskTier = RiskTier.COMMUNICATION,
                permissions = listOf(Manifest.permission.SEND_SMS),
                requiresConfirmation = true,
            ),
            capability(
                id = "calls",
                name = "Журнал звонков",
                description = "Чтение последних записей журнала звонков.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(Manifest.permission.READ_CALL_LOG),
                requiresConfirmation = true,
            ),
            capability(
                id = "call",
                name = "Звонок",
                description = "Запуск телефонного звонка.",
                riskTier = RiskTier.COMMUNICATION,
                permissions = listOf(Manifest.permission.CALL_PHONE),
                requiresConfirmation = true,
            ),
            capability(
                id = "contacts",
                name = "Контакты",
                description = "Чтение контактов.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(Manifest.permission.READ_CONTACTS),
                requiresConfirmation = true,
            ),

            capability(
                id = "location",
                name = "Геолокация",
                description = "Чтение и отправка текущей геолокации устройства.",
                riskTier = RiskTier.PERSONAL_DATA,
                permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                requiresConfirmation = true,
            ),
            capability(
                id = "photo",
                name = "Фото",
                description = "Съемка фото с камеры и отправка в Telegram.",
                riskTier = RiskTier.FILE_MEDIA,
                permissions = listOf(Manifest.permission.CAMERA),
                requiresConfirmation = true,
            ),
            capability(
                id = "record",
                name = "Запись аудио",
                description = "Запись звука с микрофона и отправка в Telegram.",
                riskTier = RiskTier.FILE_MEDIA,
                permissions = listOf(Manifest.permission.RECORD_AUDIO),
                requiresConfirmation = true,
            ),

            capability("notify", "Уведомление", "Показ локального уведомления.", RiskTier.DEVICE_CONTROL),
            capability("vibrate", "Вибрация", "Запуск вибрации устройства.", RiskTier.DEVICE_CONTROL),
            capability(
                id = "flashlight",
                name = "Фонарик",
                description = "Переключение фонарика камеры.",
                riskTier = RiskTier.DEVICE_CONTROL,
                permissions = listOf(Manifest.permission.CAMERA),
            ),
            capability("volume", "Громкость", "Изменение громкости медиа.", RiskTier.DEVICE_CONTROL),
            capability("alarm", "Сигнал", "Воспроизведение громкого локального сигнала.", RiskTier.DEVICE_CONTROL),
            capability(
                id = "brightness",
                name = "Яркость",
                description = "Чтение или изменение яркости экрана.",
                riskTier = RiskTier.DEVICE_CONTROL,
                permissions = listOf(Manifest.permission.WRITE_SETTINGS),
            ),
            capability(
                id = "bluetooth",
                name = "Bluetooth",
                description = "Чтение состояния или открытие управления Bluetooth.",
                riskTier = RiskTier.DEVICE_CONTROL,
                permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
            ),
            capability(
                id = "wifi",
                name = "Wi-Fi",
                description = "Чтение состояния Wi-Fi и открытие управления Wi-Fi.",
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
