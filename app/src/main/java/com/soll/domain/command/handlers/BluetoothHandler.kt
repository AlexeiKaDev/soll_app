package com.soll.domain.command.handlers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class BluetoothHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "bluetooth"
    override val description = "Управление Bluetooth: /bluetooth [вкл|выкл|статус]"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    override suspend fun execute(message: Message, args: String?) {
        if (bluetoothAdapter == null) {
            reply(message, "❌ Bluetooth не поддерживается на этом устройстве.")
            return
        }

        if (!hasPermission()) {
            reply(message, "❌ Нет разрешения Bluetooth. Выдайте BLUETOOTH_CONNECT в настройках приложения.")
            return
        }

        val action = args?.trim()?.lowercase() ?: "status"

        when (action) {
            "on", "вкл", "включить" -> enableBluetooth(message)
            "off", "выкл", "выключить" -> disableBluetooth(message)
            "status", "статус" -> showStatus(message)
            else -> reply(message, "Использование: /bluetooth [вкл|выкл|статус]")
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No runtime permission needed before Android 12
        }
    }

    @Suppress("MissingPermission")
    private suspend fun showStatus(message: Message) {
        val isEnabled = bluetoothAdapter?.isEnabled == true
        val text = buildString {
            append("<b>Статус Bluetooth</b>\n\n")
            append("Состояние: ${if (isEnabled) "🟢 включен" else "🔴 выключен"}\n")

            if (isEnabled) {
                bluetoothAdapter?.name?.let { name ->
                    append("Имя устройства: $name\n")
                }
            }
        }

        reply(message, text)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private suspend fun enableBluetooth(message: Message) {
        if (bluetoothAdapter?.isEnabled == true) {
            reply(message, "ℹ️ Bluetooth уже включен.")
            return
        }

        // Note: enable() is deprecated in Android 13+ and may not work
        // Users need to enable manually via settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reply(message, "⚠️ Нельзя программно включить Bluetooth на Android 13+.\n\nВключите его вручную в настройках устройства.")
            return
        }

        try {
            val result = bluetoothAdapter?.enable() == true
            if (result) {
                reply(message, "✅ Включаю Bluetooth...")
            } else {
                reply(message, "❌ Не удалось включить Bluetooth.")
            }
        } catch (e: Exception) {
            reply(message, "❌ Ошибка включения Bluetooth: ${e.message}")
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private suspend fun disableBluetooth(message: Message) {
        if (bluetoothAdapter?.isEnabled == false) {
            reply(message, "ℹ️ Bluetooth уже выключен.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reply(message, "⚠️ Нельзя программно выключить Bluetooth на Android 13+.\n\nВыключите его вручную в настройках устройства.")
            return
        }

        try {
            val result = bluetoothAdapter?.disable() == true
            if (result) {
                reply(message, "✅ Выключаю Bluetooth...")
            } else {
                reply(message, "❌ Не удалось выключить Bluetooth.")
            }
        } catch (e: Exception) {
            reply(message, "❌ Ошибка выключения Bluetooth: ${e.message}")
        }
    }
}
