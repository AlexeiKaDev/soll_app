package com.soll.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

internal class BluetoothSpeechAudioRouter(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val applicationContext = context.applicationContext

    private var previousAudioMode: Int? = null
    private var routeActive = false

    fun prepareBluetoothInput(): Boolean {
        release()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                prepareModernBluetoothInput()
            } else {
                prepareLegacyBluetoothInput()
            }
        }.onFailure { error ->
            Log.w(TAG, "Bluetooth speech route unavailable; using the built-in microphone", error)
            restoreAudioMode()
        }.getOrDefault(false)
    }

    fun release() {
        if (!routeActive) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to release Bluetooth speech route", error)
        }
        routeActive = false
        restoreAudioMode()
        Log.i(TAG, "Bluetooth speech route released")
    }

    private fun prepareModernBluetoothInput(): Boolean {
        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "BLUETOOTH_CONNECT is not granted; using the built-in microphone")
            return false
        }

        val headset = audioManager.availableCommunicationDevices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        } ?: return false

        rememberAndSetCommunicationMode()
        routeActive = audioManager.setCommunicationDevice(headset)
        if (!routeActive) {
            restoreAudioMode()
            return false
        }
        Log.i(TAG, "Bluetooth speech route selected: type=${headset.type}, name=${headset.productName}")
        return true
    }

    @Suppress("DEPRECATION")
    private fun prepareLegacyBluetoothInput(): Boolean {
        val headsetAvailable = audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .any { device -> device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (!headsetAvailable) return false

        rememberAndSetCommunicationMode()
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
        routeActive = true
        Log.i(TAG, "Legacy Bluetooth SCO speech route requested")
        return true
    }

    private fun rememberAndSetCommunicationMode() {
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun restoreAudioMode() {
        previousAudioMode?.let { mode -> audioManager.mode = mode }
        previousAudioMode = null
    }

    private companion object {
        const val TAG = "SollVoiceAudio"
    }
}
