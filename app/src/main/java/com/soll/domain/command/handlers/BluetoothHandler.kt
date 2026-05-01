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
    override val description = "Bluetooth control: /bluetooth [on|off|status]"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    override suspend fun execute(message: Message, args: String?) {
        if (bluetoothAdapter == null) {
            reply(message, "❌ Bluetooth is not supported on this device.")
            return
        }

        if (!hasPermission()) {
            reply(message, "❌ Bluetooth permission not granted. Please grant BLUETOOTH_CONNECT permission in app settings.")
            return
        }

        val action = args?.trim()?.lowercase() ?: "status"

        when (action) {
            "on" -> enableBluetooth(message)
            "off" -> disableBluetooth(message)
            "status" -> showStatus(message)
            else -> reply(message, "Usage: /bluetooth [on|off|status]")
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
        val state = if (isEnabled) "ON" else "OFF"

        val text = buildString {
            append("<b>Bluetooth Status</b>\n\n")
            append("State: ${if (isEnabled) "🟢 ON" else "🔴 OFF"}\n")

            if (isEnabled) {
                bluetoothAdapter?.name?.let { name ->
                    append("Device Name: $name\n")
                }
                bluetoothAdapter?.address?.let { address ->
                    append("MAC: $address\n")
                }
            }
        }

        reply(message, text)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private suspend fun enableBluetooth(message: Message) {
        if (bluetoothAdapter?.isEnabled == true) {
            reply(message, "ℹ️ Bluetooth is already ON.")
            return
        }

        // Note: enable() is deprecated in Android 13+ and may not work
        // Users need to enable manually via settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reply(message, "⚠️ Cannot programmatically enable Bluetooth on Android 13+.\n\nPlease enable it manually in device settings.")
            return
        }

        try {
            val result = bluetoothAdapter?.enable() == true
            if (result) {
                reply(message, "✅ Bluetooth turning ON...")
            } else {
                reply(message, "❌ Failed to enable Bluetooth.")
            }
        } catch (e: Exception) {
            reply(message, "❌ Error enabling Bluetooth: ${e.message}")
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private suspend fun disableBluetooth(message: Message) {
        if (bluetoothAdapter?.isEnabled == false) {
            reply(message, "ℹ️ Bluetooth is already OFF.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reply(message, "⚠️ Cannot programmatically disable Bluetooth on Android 13+.\n\nPlease disable it manually in device settings.")
            return
        }

        try {
            val result = bluetoothAdapter?.disable() == true
            if (result) {
                reply(message, "✅ Bluetooth turning OFF...")
            } else {
                reply(message, "❌ Failed to disable Bluetooth.")
            }
        } catch (e: Exception) {
            reply(message, "❌ Error disabling Bluetooth: ${e.message}")
        }
    }
}
