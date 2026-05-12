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
    override val description = "Показать статус устройства: батарея, память, сеть"

    override suspend fun execute(message: Message, args: String?) {
        val batteryInfo = getBatteryInfo()
        val memoryInfo = getMemoryInfo()
        val networkInfo = getNetworkInfo()
        val uptimeInfo = getUptimeInfo()
        val botInfo = getBotInfo()

        val text = """
            |<b>Статус устройства</b>
            |
            |<b>Батарея:</b>
            |$batteryInfo
            |
            |<b>Память:</b>
            |$memoryInfo
            |
            |<b>Сеть:</b>
            |$networkInfo
            |
            |<b>Время работы:</b>
            |$uptimeInfo
            |
            |<b>Сервис бота:</b>
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
            BatteryManager.BATTERY_PLUGGED_AC -> "сеть"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "беспроводная зарядка"
            else -> "батарея"
        }

        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            ?.div(10.0) ?: 0.0

        return buildString {
            append("Уровень: $percentage%")
            if (isCharging) append(" (заряжается: $chargingSource)")
            append("\nТемпература: ${temperature}°C")
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
            append("Всего: ${df.format(totalMB / 1024.0)} GB\n")
            append("Занято: ${df.format(usedMB / 1024.0)} GB (${df.format(usedPercent)}%)\n")
            append("Доступно: ${df.format(availMB / 1024.0)} GB")
            if (memoryInfo.lowMemory) append("\n⚠️ Мало памяти")
        }
    }

    private fun getNetworkInfo(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        return if (capabilities != null) {
            val type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобильная сеть"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "неизвестно"
            }
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            "Тип: $type\nИнтернет: ${if (hasInternet) "доступен" else "недоступен"}"
        } else {
            "Нет подключения к сети"
        }
    }

    private fun getUptimeInfo(): String {
        val uptimeMs = SystemClock.elapsedRealtime()
        val hours = uptimeMs / (1000 * 60 * 60)
        val minutes = (uptimeMs / (1000 * 60)) % 60

        return "Устройство: ${hours} ч ${minutes} мин"
    }

    private fun getBotInfo(): String {
        val isRunning = BotService.isRunning.value
        val messagesProcessed = BotService.messagesProcessed
        val startTime = BotService.startTime

        return if (isRunning && startTime > 0) {
            val uptimeMs = System.currentTimeMillis() - startTime
            val hours = uptimeMs / (1000 * 60 * 60)
            val minutes = (uptimeMs / (1000 * 60)) % 60
            "Статус: запущен\nВремя работы: ${hours} ч ${minutes} мин\nСообщений: $messagesProcessed"
        } else {
            "Статус: остановлен"
        }
    }
}
