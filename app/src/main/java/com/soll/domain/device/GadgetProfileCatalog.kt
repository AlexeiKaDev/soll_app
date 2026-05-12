package com.soll.domain.device

enum class GadgetDomain(val title: String) {
    AQUARIUM_GREENHOUSE("Аквариум / теплица"),
    ESP_CONTROLLER("ESP-контроллер"),
}

enum class GadgetCommunicationStatus(val title: String) {
    ACTIVE("работает"),
    PLANNED("в плане"),
    HARDWARE_DEPENDENT("по железу"),
}

data class GadgetCommunicationOption(
    val title: String,
    val transport: String,
    val status: GadgetCommunicationStatus,
    val note: String,
) {
    val chipText: String = "$title: ${status.title}"
}

data class GadgetProfileDescriptor(
    val profileId: String,
    val title: String,
    val domain: GadgetDomain,
    val summary: String,
    val setupHint: String,
    val protocolName: String,
    val primaryUseCases: List<String>,
    val communicationOptions: List<GadgetCommunicationOption>,
    val expectedSensors: List<String>,
    val expectedActuators: List<String>,
    val plannedModules: List<String>,
)

object GadgetProfileCatalog {
    private val descriptors = listOf(
        GadgetProfileDescriptor(
            profileId = AquikDeviceProfile.ID,
            title = "Aquik v2",
            domain = GadgetDomain.AQUARIUM_GREENHOUSE,
            summary = "Headless-профиль для управления аквариумом или теплицей через специальный протокол Soll/Aquik.",
            setupHint = "ESP не поднимает пользовательский web UI: Android-приложение отправляет команды напрямую по выбранному каналу связи.",
            protocolName = "Soll Gadget Protocol / Aquik v2",
            primaryUseCases = listOf("аквариум", "теплица", "датчики среды", "свет и насосы"),
            communicationOptions = listOf(
                GadgetCommunicationOption(
                    title = "Wi-Fi LAN",
                    transport = "WebSocket JSON",
                    status = GadgetCommunicationStatus.ACTIVE,
                    note = "Основной режим после подключения гаджета к домашней сети.",
                ),
                GadgetCommunicationOption(
                    title = "Wi-Fi AP",
                    transport = "локальная настройка + WebSocket/HTTP bootstrap",
                    status = GadgetCommunicationStatus.ACTIVE,
                    note = "Первичная привязка через ${AquikProvisioningDefaults.setupApSsid}; пользовательского ESP-интерфейса нет.",
                ),
                GadgetCommunicationOption(
                    title = "BLE",
                    transport = "GATT",
                    status = GadgetCommunicationStatus.PLANNED,
                    note = "Для ESP32/ESP32-C3: привязка, короткие команды и диагностика без Wi-Fi.",
                ),
                GadgetCommunicationOption(
                    title = "Bluetooth",
                    transport = "SPP",
                    status = GadgetCommunicationStatus.HARDWARE_DEPENDENT,
                    note = "Только для ESP32 с Bluetooth Classic; ESP32-C3 и ESP8266 не поддерживают SPP.",
                ),
                GadgetCommunicationOption(
                    title = "Сервер Soll",
                    transport = "HTTPS/JSON",
                    status = GadgetCommunicationStatus.ACTIVE,
                    note = "ESP отправляет heartbeat, телеметрию и события на сервер; Android читает актуальное состояние и историю.",
                ),
            ),
            expectedSensors = listOf(
                "Температура воды",
                "Температура воздуха",
                "Влажность",
                "Давление",
                "Уровень воды",
                "Свет",
                "TDS",
                "CO2",
            ),
            expectedActuators = listOf(
                "Воздушный насос",
                "Водяной насос",
                "Вентилятор",
                "Полный спектр",
                "Белый LED",
            ),
            plannedModules = listOf(
                "настройки гаджета",
                "калибровка датчиков",
                "расписания",
                "автоматизации",
                "диагностика и OTA",
            ),
        ),
        GadgetProfileDescriptor(
            profileId = GenericEspWebSocketProfile.ID,
            title = "ESP WebSocket",
            domain = GadgetDomain.ESP_CONTROLLER,
            summary = "Универсальный headless-профиль для будущих ESP-гаджетов со специальным JSON-протоколом.",
            setupHint = "Используется ручное подключение или QR с host, port, path и profileId; UI остается только в Android.",
            protocolName = "Soll Gadget Protocol",
            primaryUseCases = listOf("прототипы", "датчики", "реле", "лабораторные стенды"),
            communicationOptions = listOf(
                GadgetCommunicationOption(
                    title = "Wi-Fi LAN",
                    transport = "WebSocket JSON",
                    status = GadgetCommunicationStatus.ACTIVE,
                    note = "Базовый сетевой транспорт для новых гаджетов.",
                ),
                GadgetCommunicationOption(
                    title = "Wi-Fi AP",
                    transport = "локальная привязка",
                    status = GadgetCommunicationStatus.PLANNED,
                    note = "Первичная настройка без встроенного ESP UI.",
                ),
                GadgetCommunicationOption(
                    title = "BLE",
                    transport = "GATT",
                    status = GadgetCommunicationStatus.PLANNED,
                    note = "Единый BLE-transport для совместимых ESP.",
                ),
                GadgetCommunicationOption(
                    title = "Bluetooth",
                    transport = "SPP",
                    status = GadgetCommunicationStatus.HARDWARE_DEPENDENT,
                    note = "Опционально для ESP32 Classic BT.",
                ),
                GadgetCommunicationOption(
                    title = "Сервер Soll",
                    transport = "HTTPS/JSON",
                    status = GadgetCommunicationStatus.ACTIVE,
                    note = "Удаленный read-route для heartbeat, телеметрии, событий и будущей очереди команд.",
                ),
            ),
            expectedSensors = listOf("произвольные JSON-датчики"),
            expectedActuators = listOf("произвольные безопасные команды профиля"),
            plannedModules = listOf("автопоиск", "шаблоны команд", "профили виджетов"),
        ),
    )

    val all: List<GadgetProfileDescriptor> = descriptors

    fun byProfileId(profileId: String): GadgetProfileDescriptor? =
        descriptors.firstOrNull { it.profileId == profileId }

    fun forProfile(profile: DeviceProfile): GadgetProfileDescriptor =
        byProfileId(profile.id) ?: GadgetProfileDescriptor(
            profileId = profile.id,
            title = profile.name,
            domain = GadgetDomain.ESP_CONTROLLER,
            summary = "Пользовательский профиль гаджета.",
            setupHint = "Подключение выполняется по параметрам выбранного профиля.",
            protocolName = profile.commandSchemaVersion,
            primaryUseCases = emptyList(),
            communicationOptions = listOf(
                GadgetCommunicationOption(
                    title = "Wi-Fi LAN",
                    transport = profile.transport.name,
                    status = GadgetCommunicationStatus.ACTIVE,
                    note = "Пользовательский профиль использует указанный транспорт.",
                ),
                GadgetCommunicationOption(
                    title = "Сервер Soll",
                    transport = "HTTPS/JSON",
                    status = GadgetCommunicationStatus.PLANNED,
                    note = "Можно подключить к общему серверному маршруту телеметрии после описания профиля.",
                ),
            ),
            expectedSensors = emptyList(),
            expectedActuators = profile.capabilities,
            plannedModules = emptyList(),
        )
}
