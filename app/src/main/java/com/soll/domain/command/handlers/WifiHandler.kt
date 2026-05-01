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
    override val description = "WiFi info: /wifi [status]"

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override suspend fun execute(message: Message, args: String?) {
        val action = args?.trim()?.lowercase() ?: "status"

        when (action) {
            "status", "" -> showStatus(message)
            "on", "off" -> showToggleNotSupported(message)
            else -> reply(message, "Usage: /wifi [status]\n\n<i>Note: WiFi toggle is not supported on Android 10+</i>")
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun showStatus(message: Message) {
        val isEnabled = wifiManager.isWifiEnabled
        val isConnected = isWifiConnected()

        val text = buildString {
            append("<b>📶 WiFi Status</b>\n\n")
            append("WiFi: ${if (isEnabled) "🟢 ON" else "🔴 OFF"}\n")

            if (isEnabled && isConnected) {
                append("Status: Connected\n\n")

                // Get connection info
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null) {
                    val ssid = wifiInfo.ssid?.replace("\"", "") ?: "Unknown"
                    if (ssid != "<unknown ssid>") {
                        append("<b>Network:</b> $ssid\n")
                    }

                    val rssi = wifiInfo.rssi
                    val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)
                    val signalPercent = signalLevel * 25
                    append("<b>Signal:</b> $signalPercent% (${getSignalDescription(signalLevel)})\n")

                    val linkSpeed = wifiInfo.linkSpeed
                    if (linkSpeed > 0) {
                        append("<b>Speed:</b> $linkSpeed Mbps\n")
                    }

                    val frequency = wifiInfo.frequency
                    val band = if (frequency > 4900) "5 GHz" else "2.4 GHz"
                    append("<b>Band:</b> $band\n")

                    // IP Address
                    val ip = intToIp(wifiInfo.ipAddress)
                    if (ip != "0.0.0.0") {
                        append("<b>IP:</b> $ip\n")
                    }
                }
            } else if (isEnabled) {
                append("Status: Not connected\n")
            }

            append("\n<i>Note: WiFi toggle is not supported on Android 10+. ")
            append("Use device settings to enable/disable WiFi.</i>")
        }

        reply(message, text)
    }

    private suspend fun showToggleNotSupported(message: Message) {
        reply(
            message,
            "⚠️ WiFi toggle is not supported on Android 10+.\n\n" +
            "Due to Android restrictions, apps cannot programmatically enable or disable WiFi.\n\n" +
            "Please use device settings to toggle WiFi."
        )
    }

    private fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getSignalDescription(level: Int): String {
        return when (level) {
            4 -> "Excellent"
            3 -> "Good"
            2 -> "Fair"
            1 -> "Weak"
            else -> "Very Weak"
        }
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
