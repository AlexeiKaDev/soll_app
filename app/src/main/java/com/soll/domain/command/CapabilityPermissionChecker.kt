package com.soll.domain.command

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.soll.domain.assistant.Capability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class MissingCapabilityPermission(
    val permission: String,
    val label: String,
    val specialAccess: Boolean = false,
)

interface CapabilityPermissionChecker {
    fun missingPermissions(capability: Capability): List<MissingCapabilityPermission>
}

object AllowAllCapabilityPermissionChecker : CapabilityPermissionChecker {
    override fun missingPermissions(capability: Capability): List<MissingCapabilityPermission> = emptyList()
}

@Singleton
@SuppressLint("InlinedApi")
class AndroidCapabilityPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : CapabilityPermissionChecker {
    override fun missingPermissions(capability: Capability): List<MissingCapabilityPermission> =
        capability.requiredAndroidPermissions.mapNotNull { permission ->
            if (isGranted(permission)) {
                null
            } else {
                MissingCapabilityPermission(
                    permission = permission,
                    label = permission.ruLabel(),
                    specialAccess = permission.isSpecialAccess(),
                )
            }
        }

    private fun isGranted(permission: String): Boolean {
        return when (permission) {
            Manifest.permission.WRITE_SETTINGS -> Settings.System.canWrite(context)
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
            }
            Manifest.permission.READ_EXTERNAL_STORAGE -> {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            Manifest.permission.BLUETOOTH_CONNECT -> {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            Manifest.permission.NFC,
            Manifest.permission.ACCESS_WIFI_STATE -> true
            else -> ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun String.isSpecialAccess(): Boolean =
        this == Manifest.permission.WRITE_SETTINGS ||
            this == Manifest.permission.MANAGE_EXTERNAL_STORAGE

    private fun String.ruLabel(): String = when (this) {
        Manifest.permission.CAMERA -> "камера"
        Manifest.permission.RECORD_AUDIO -> "микрофон"
        Manifest.permission.ACCESS_FINE_LOCATION -> "геолокация"
        Manifest.permission.READ_SMS -> "чтение SMS"
        Manifest.permission.SEND_SMS -> "отправка SMS"
        Manifest.permission.READ_CALL_LOG -> "журнал звонков"
        Manifest.permission.CALL_PHONE -> "телефонные звонки"
        Manifest.permission.READ_CONTACTS -> "контакты"
        Manifest.permission.READ_EXTERNAL_STORAGE -> "чтение файлов"
        Manifest.permission.MANAGE_EXTERNAL_STORAGE -> "доступ ко всем файлам"
        Manifest.permission.WRITE_SETTINGS -> "изменение системных настроек"
        Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth"
        Manifest.permission.ACCESS_WIFI_STATE -> "состояние Wi-Fi"
        Manifest.permission.NFC -> "NFC"
        else -> this.substringAfterLast('.')
    }
}
