package com.soll.domain.command.handlers

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class WifiHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "wifi"
    override val description = "Статус Wi-Fi: /wifi [статус]"

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override suspend fun execute(message: Message, args: String?) {
        val action = args?.trim()?.lowercase() ?: "status"

        when (action) {
            "status", "статус", "" -> showStatus(message)
            "on", "off", "вкл", "выкл", "включить", "выключить" -> showToggleNotSupported(message)
            else -> reply(message, "Использование: /wifi [статус]\n\n<i>Переключение Wi-Fi недоступно на Android 10+</i>")
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun showStatus(message: Message) {
        val isEnabled = wifiManager.isWifiEnabled
        val isConnected = isWifiConnected()

        val text = buildString {
            append("<b>📶 Статус Wi-Fi</b>\n\n")
            append("Wi-Fi: ${if (isEnabled) "🟢 включен" else "🔴 выключен"}\n")

            if (isEnabled && isConnected) {
                append("Статус: подключено\n\n")

                // Get connection info
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null) {
                    val ssid = wifiInfo.ssid?.replace("\"", "") ?: "неизвестно"
                    if (ssid != "<unknown ssid>") {
                        append("<b>Сеть:</b> $ssid\n")
                    }

                    val rssi = wifiInfo.rssi
                    val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)
                    val signalPercent = signalLevel * 25
                    append("<b>Сигнал:</b> $signalPercent% (${getSignalDescription(signalLevel)})\n")

                    val linkSpeed = wifiInfo.linkSpeed
                    if (linkSpeed > 0) {
                        append("<b>Скорость:</b> $linkSpeed Mbps\n")
                    }

                    val frequency = wifiInfo.frequency
                    val band = if (frequency > 4900) "5 GHz" else "2.4 GHz"
                    append("<b>Диапазон:</b> $band\n")

                    // IP Address
                    val ip = intToIp(wifiInfo.ipAddress)
                    if (ip != "0.0.0.0") {
                        append("<b>IP:</b> $ip\n")
                    }
                }
            } else if (isEnabled) {
                append("Статус: не подключено\n")
            }

            append("\n<i>Переключение Wi-Fi недоступно на Android 10+. ")
            append("Включайте и выключайте Wi-Fi через настройки устройства.</i>")
        }

        reply(message, text)
    }

    private suspend fun showToggleNotSupported(message: Message) {
        reply(
            message,
            "⚠️ Переключение Wi-Fi недоступно на Android 10+.\n\n" +
            "Из-за ограничений Android приложения не могут программно включать или выключать Wi-Fi.\n\n" +
            "Используйте настройки устройства."
        )
    }

    private fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getSignalDescription(level: Int): String {
        return when (level) {
            4 -> "отличный"
            3 -> "хороший"
            2 -> "средний"
            1 -> "слабый"
            else -> "очень слабый"
        }
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
