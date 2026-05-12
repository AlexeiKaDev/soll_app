package com.soll.domain.device

data class DeviceProvisioningStep(
    val title: String,
    val description: String,
)

object AquikProvisioningDefaults {
    const val setupApSsid = "AQUIK-Setup"
    const val setupApPassword = "aquik12345"
    const val setupApHost = "192.168.4.1"
    const val wifiConfigureEndpoint = "/api/wifi/configure"
    const val smartConfigEndpoint = "/api/smartconfig/start"
    const val connectionStatusEndpoint = "/api/connection/status"
    const val defaultSmartConfigTimeoutSec = 60
}

object DeviceProvisioningPlan {
    fun aquikSetupSteps(): List<DeviceProvisioningStep> = listOf(
        DeviceProvisioningStep(
            title = "Включите режим настройки",
            description = "После первого запуска или сброса Aquik поднимает точку ${AquikProvisioningDefaults.setupApSsid}.",
        ),
        DeviceProvisioningStep(
            title = "Подключите телефон к AP",
            description = "SSID: ${AquikProvisioningDefaults.setupApSsid}, пароль: ${AquikProvisioningDefaults.setupApPassword}.",
        ),
        DeviceProvisioningStep(
            title = "Отправьте Wi-Fi",
            description = "Приложение передает SSID и пароль в локальный endpoint ${AquikProvisioningDefaults.wifiConfigureEndpoint}.",
        ),
        DeviceProvisioningStep(
            title = "Вернитесь в домашнюю сеть",
            description = "После подключения гаджета укажите его новый IP в ручном подключении или найдите его через роутер.",
        ),
    )
}
