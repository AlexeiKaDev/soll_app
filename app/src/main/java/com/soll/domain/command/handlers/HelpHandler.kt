package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class HelpHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "help"
    override val description = "Показать все доступные команды"

    override suspend fun execute(message: Message, args: String?) {
        val text = """
            |<b>Команды Soll</b>
            |
            |<b>Система:</b>
            |/start - приветствие
            |/help - эта справка
            |/ping - проверить ответ бота
            |/status - статус устройства
            |/info - информация об устройстве
            |/storage - хранилище
            |/logs - последние логи команд
            |/jobs [id] - задачи инструментов
            |/sync now - проверить сервер Soll и доску задач
            |/raw &lt;текст&gt; - отправить заметку в Soll
            |
            |<b>Файлы:</b>
            |/files [путь] - список файлов в папке
            |/download &lt;путь&gt; - отправить файл
            |
            |<b>SMS и звонки:</b>
            |/sms [кол-во] - прочитать SMS (по умолчанию 10)
            |/sms_send &lt;номер&gt; &lt;текст&gt; - отправить SMS
            |/calls [кол-во] - журнал звонков (по умолчанию 15)
            |/call &lt;номер&gt; - позвонить
            |/contacts [запрос] - контакты или поиск
            |
            |<b>Медиа:</b>
            |/location - получить GPS-геолокацию
            |/photo [передняя|задняя] - сделать фото
            |/record [секунды] - записать аудио (до 60 секунд)
            |
            |<b>Управление устройством:</b>
            |/notify [текст] - показать уведомление
            |/vibrate [мс] - вибрация (по умолчанию 500 мс)
            |/flashlight [вкл|выкл] - фонарик
            |/volume [0-100] - громкость медиа
            |/brightness [0-100|авто|ручной] - яркость
            |/alarm [секунды] - громкий сигнал (до 30 секунд)
            |/bluetooth [вкл|выкл|статус] - Bluetooth
            |/wifi - статус Wi-Fi
            |
            |Рискованные команды выполняются только с <code>--confirm</code> в конце.
        """.trimMargin()

        reply(message, text)
    }
}
