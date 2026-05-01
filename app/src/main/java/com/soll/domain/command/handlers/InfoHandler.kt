package com.soll.domain.command.handlers

import android.content.Context
import android.os.Build
import com.soll.BuildConfig
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import java.util.*

class InfoHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "info"
    override val description = "Get device information"

    override suspend fun execute(message: Message, args: String?) {
        val text = """
            |<b>Device Information</b>
            |
            |<b>Device:</b>
            |Manufacturer: ${Build.MANUFACTURER}
            |Model: ${Build.MODEL}
            |Brand: ${Build.BRAND}
            |Device: ${Build.DEVICE}
            |
            |<b>Android:</b>
            |Version: Android ${Build.VERSION.RELEASE}
            |API Level: ${Build.VERSION.SDK_INT}
            |Security Patch: ${Build.VERSION.SECURITY_PATCH}
            |
            |<b>System:</b>
            |Board: ${Build.BOARD}
            |Hardware: ${Build.HARDWARE}
            |Bootloader: ${Build.BOOTLOADER}
            |
            |<b>Build:</b>
            |Build ID: ${Build.ID}
            |Fingerprint: ${Build.FINGERPRINT.take(50)}...
            |
            |<b>Soll:</b>
            |Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            |Package: ${BuildConfig.APPLICATION_ID}
            |Debug: ${BuildConfig.DEBUG}
            |
            |<b>Locale:</b>
            |${Locale.getDefault().displayName}
            |Timezone: ${TimeZone.getDefault().id}
        """.trimMargin()

        reply(message, text)
    }
}
