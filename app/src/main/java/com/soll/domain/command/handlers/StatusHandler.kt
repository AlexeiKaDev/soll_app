package com.soll.domain.command.handlers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.data.service.BotService
import com.soll.domain.command.CommandHandler
import java.text.DecimalFormat

class StatusHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "status"
    override val description = "Get device status (battery, memory, network)"

    override suspend fun execute(message: Message, args: String?) {
        val batteryInfo = getBatteryInfo()
        val memoryInfo = getMemoryInfo()
        val networkInfo = getNetworkInfo()
        val uptimeInfo = getUptimeInfo()
        val botInfo = getBotInfo()

        val text = """
            |<b>Device Status</b>
            |
            |<b>Battery:</b>
            |$batteryInfo
            |
            |<b>Memory:</b>
            |$memoryInfo
            |
            |<b>Network:</b>
            |$networkInfo
            |
            |<b>Uptime:</b>
            |$uptimeInfo
            |
            |<b>Bot Service:</b>
            |$botInfo
        """.trimMargin()

        reply(message, text)
    }

    private fun getBatteryInfo(): String {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percentage = (level * 100 / scale)

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }

        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            ?.div(10.0) ?: 0.0

        return buildString {
            append("Level: $percentage%")
            if (isCharging) append(" (Charging via $chargingSource)")
            append("\nTemperature: ${temperature}°C")
        }
    }

    private fun getMemoryInfo(): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val df = DecimalFormat("#.##")
        val totalMB = memoryInfo.totalMem / (1024 * 1024)
        val availMB = memoryInfo.availMem / (1024 * 1024)
        val usedMB = totalMB - availMB
        val usedPercent = (usedMB * 100.0 / totalMB)

        return buildString {
            append("Total: ${df.format(totalMB / 1024.0)} GB\n")
            append("Used: ${df.format(usedMB / 1024.0)} GB (${df.format(usedPercent)}%)\n")
            append("Available: ${df.format(availMB / 1024.0)} GB")
            if (memoryInfo.lowMemory) append("\n⚠️ Low memory!")
        }
    }

    private fun getNetworkInfo(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        return if (capabilities != null) {
            val type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Unknown"
            }
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            "Type: $type\nInternet: ${if (hasInternet) "Available" else "Not Available"}"
        } else {
            "No network connection"
        }
    }

    private fun getUptimeInfo(): String {
        val uptimeMs = SystemClock.elapsedRealtime()
        val hours = uptimeMs / (1000 * 60 * 60)
        val minutes = (uptimeMs / (1000 * 60)) % 60

        return "Device: ${hours}h ${minutes}m"
    }

    private fun getBotInfo(): String {
        val isRunning = BotService.isRunning.value
        val messagesProcessed = BotService.messagesProcessed
        val startTime = BotService.startTime

        return if (isRunning && startTime > 0) {
            val uptimeMs = System.currentTimeMillis() - startTime
            val hours = uptimeMs / (1000 * 60 * 60)
            val minutes = (uptimeMs / (1000 * 60)) % 60
            "Status: Running\nUptime: ${hours}h ${minutes}m\nMessages: $messagesProcessed"
        } else {
            "Status: Stopped"
        }
    }
}
