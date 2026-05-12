package com.soll.domain.command

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.local.entity.CommandLogEntity
import com.soll.data.repository.NoteRepository
import com.soll.data.repository.TelegramRepository
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.command.handlers.*
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.tool.ToolHandler
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobProgressSink
import com.soll.domain.tool.ToolJobResult
import com.soll.domain.tool.ToolJobRunner
import com.soll.domain.tool.ToolJobStatus
import com.soll.domain.tool.ToolJobStore
import com.soll.domain.soll.SollGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Singleton

@Singleton
class CommandProcessor(
    private val context: Context?,
    private val telegramRepository: TelegramRepository?,
    private val commandGateway: CommandExecutionGateway,
    private val capabilityRegistry: CapabilityRegistry,
    private val commandSafetyGate: CommandSafetyGate = CommandSafetyGate(capabilityRegistry),
    private val assistantEventLogger: AssistantEventLogger,
    private val notificationCenter: SollNotificationCenter? = null,
    private val toolJobRunner: ToolJobRunner? = null,
    private val toolJobStore: ToolJobStore? = null,
    private val sollGateway: SollGateway? = null,
    private val noteRepository: NoteRepository? = null,
    private val providedHandlers: Map<String, CommandExecutable>? = null,
) {
    constructor(
        @ApplicationContext context: Context,
        telegramRepository: TelegramRepository,
        commandGateway: CommandExecutionGateway,
        capabilityRegistry: CapabilityRegistry,
        commandSafetyGate: CommandSafetyGate,
        assistantEventLogger: AssistantEventLogger,
        notificationCenter: SollNotificationCenter,
        toolJobRunner: ToolJobRunner,
        toolJobStore: ToolJobStore,
        sollGateway: SollGateway,
        noteRepository: NoteRepository,
    ) : this(
        context = context,
        telegramRepository = telegramRepository,
        commandGateway = commandGateway,
        capabilityRegistry = capabilityRegistry,
        commandSafetyGate = commandSafetyGate,
        assistantEventLogger = assistantEventLogger,
        notificationCenter = notificationCenter,
        toolJobRunner = toolJobRunner,
        toolJobStore = toolJobStore,
        sollGateway = sollGateway,
        noteRepository = noteRepository,
        providedHandlers = null,
    )

    internal constructor(
        commandGateway: CommandExecutionGateway,
        capabilityRegistry: CapabilityRegistry,
        assistantEventLogger: AssistantEventLogger,
        handlers: Map<String, CommandExecutable>,
        commandSafetyGate: CommandSafetyGate = CommandSafetyGate(capabilityRegistry),
        toolJobRunner: ToolJobRunner? = null,
        toolJobStore: ToolJobStore? = null,
        sollGateway: SollGateway? = null,
        noteRepository: NoteRepository? = null,
    ) : this(
        context = null,
        telegramRepository = null,
        commandGateway = commandGateway,
        capabilityRegistry = capabilityRegistry,
        commandSafetyGate = commandSafetyGate,
        assistantEventLogger = assistantEventLogger,
        notificationCenter = null,
        toolJobRunner = toolJobRunner,
        toolJobStore = toolJobStore,
        sollGateway = sollGateway,
        noteRepository = noteRepository,
        providedHandlers = handlers,
    )

    private val handlers: Map<String, CommandExecutable> by lazy {
        providedHandlers ?: createDefaultHandlers()
    }

    private val jobBackedCommands = setOf("photo", "record", "download")

    private fun createDefaultHandlers(): Map<String, CommandExecutable> {
        val context = requireNotNull(context) { "Context is required for default command handlers" }
        val telegramRepository = requireNotNull(telegramRepository) {
            "TelegramRepository is required for default command handlers"
        }

        val handlers = mutableMapOf<String, CommandExecutable>(
            // System commands
            "start" to StartHandler(context, telegramRepository),
            "help" to HelpHandler(context, telegramRepository),
            "ping" to PingHandler(context, telegramRepository),
            "status" to StatusHandler(context, telegramRepository),
            "info" to InfoHandler(context, telegramRepository),
            "logs" to LogsHandler(context, telegramRepository),
            "storage" to StorageHandler(context, telegramRepository),

            // File operations
            "files" to FilesHandler(context, telegramRepository),
            "download" to DownloadHandler(context, telegramRepository),

            // SMS & Calls
            "sms" to SmsHandler(context, telegramRepository),
            "sms_send" to SmsSendHandler(context, telegramRepository),
            "calls" to CallsHandler(context, telegramRepository),
            "call" to CallHandler(context, telegramRepository),
            "contacts" to ContactsHandler(context, telegramRepository),

            // Location, Camera & Recording
            "location" to LocationHandler(context, telegramRepository),
            "photo" to PhotoHandler(context, telegramRepository),
            "record" to RecordHandler(context, telegramRepository),

            // Device control
            "notify" to NotifyHandler(
                context,
                telegramRepository,
                requireNotNull(notificationCenter) {
                    "SollNotificationCenter is required for default notify handler"
                },
            ),
            "vibrate" to VibrateHandler(context, telegramRepository),
            "flashlight" to FlashlightHandler(context, telegramRepository),
            "volume" to VolumeHandler(context, telegramRepository),
            "alarm" to AlarmHandler(context, telegramRepository),
            "brightness" to BrightnessHandler(context, telegramRepository),
            "bluetooth" to BluetoothHandler(context, telegramRepository),
            "wifi" to WifiHandler(context, telegramRepository)
        )
        toolJobStore?.let {
            handlers["jobs"] = JobsHandler(context, telegramRepository, it)
        }
        sollGateway?.let {
            handlers["sync"] = SyncHandler(context, telegramRepository, it)
        }
        noteRepository?.let {
            handlers["raw"] = RawNoteHandler(context, telegramRepository, it)
        }
        return handlers
    }

    suspend fun processCommand(
        command: String,
        args: String?,
        message: Message
    ) {
        val normalizedCommand = command.lowercase()
        val handler = handlers[normalizedCommand]
        val startTime = System.currentTimeMillis()

        if (handler == null) {
            // Unknown command
            val response = "Неизвестная команда: /$command\n\nИспользуйте /help, чтобы увидеть доступные команды."
            commandGateway.sendMessage(message.chat.id, response)

            commandGateway.logCommand(
                command = command,
                args = args,
                chatId = message.chat.id,
                userId = message.from?.id,
                username = message.from?.username,
                status = CommandLogEntity.STATUS_ERROR,
                errorMessage = "Неизвестная команда"
            )
            return
        }

        val safetyDecision = commandSafetyGate.evaluate(normalizedCommand, args)
        if (!safetyDecision.allowed) {
            blockCommand(command, args, message, startTime, safetyDecision)
            return
        }
        val safeArgs = safetyDecision.normalizedArgs

        if (normalizedCommand in jobBackedCommands && toolJobRunner != null) {
            processJobBackedCommand(
                command = normalizedCommand,
                args = safeArgs,
                message = message,
                handler = handler,
                startTime = startTime,
            )
            return
        }

        try {
            Timber.d("Executing command: $command")
            handler.execute(message, safeArgs)

            commandGateway.logCommand(
                command = command,
                args = safeArgs,
                chatId = message.chat.id,
                userId = message.from?.id,
                username = message.from?.username,
                status = CommandLogEntity.STATUS_SUCCESS,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Timber.e(e, "Error executing command: $command")

            val errorResponse = "Ошибка выполнения /$command: ${e.message}"
            commandGateway.sendMessage(message.chat.id, errorResponse)

            commandGateway.logCommand(
                command = command,
                args = args,
                chatId = message.chat.id,
                userId = message.from?.id,
                username = message.from?.username,
                status = CommandLogEntity.STATUS_ERROR,
                errorMessage = e.message,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun blockCommand(
        command: String,
        args: String?,
        message: Message,
        startTime: Long,
        decision: CommandSafetyDecision,
    ) {
        val response = buildString {
            append("Команда /$command заблокирована.")
            if (decision.message.isNotBlank()) {
                append("\nПричина: ${decision.message}")
            }
            if (decision.reason != CommandSafetyBlockReason.CONFIRMATION_REQUIRED) {
                append("\nОткройте Настройки > Возможности и разрешения Android.")
            }
        }

        commandGateway.sendMessage(message.chat.id, response)
        commandGateway.logCommand(
            command = command,
            args = args,
            chatId = message.chat.id,
            userId = message.from?.id,
            username = message.from?.username,
            status = CommandLogEntity.STATUS_ERROR,
            errorMessage = decision.message.ifBlank { "Заблокировано политикой безопасности" },
            responseText = response,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
        assistantEventLogger.logEvent(
            AssistantEvent(
                type = if (decision.reason == CommandSafetyBlockReason.CAPABILITY_POLICY) {
                    "capability_blocked"
                } else {
                    "command_safety_blocked"
                },
                source = "telegram",
                summary = "Заблокирована /$command: ${decision.message.ifBlank { "политика безопасности" }}",
                payloadJson = buildBlockedPayloadJson(command, args, message, decision),
            )
        )
    }

    private suspend fun processJobBackedCommand(
        command: String,
        args: String?,
        message: Message,
        handler: CommandExecutable,
        startTime: Long,
    ) {
        val inputJson = buildCommandInputJson(command, args, message)
        val job = requireNotNull(toolJobRunner).run(
            toolId = command,
            inputJson = inputJson,
            handler = CommandExecutableToolHandler(command, handler, message, args),
            onQueued = { queued ->
                commandGateway.sendMessage(
                    chatId = message.chat.id,
                    text = buildString {
                        append("Создана задача инструмента <b>/$command</b>.\n")
                        append("ID: <code>${queued.id}</code>\n")
                        append("Статус: <code>/jobs ${queued.id}</code>")
                    },
                    replyToMessageId = message.messageId,
                )
            },
        )

        val isSuccess = job.status == ToolJobStatus.SUCCESS
        val status = if (isSuccess) CommandLogEntity.STATUS_SUCCESS else CommandLogEntity.STATUS_ERROR
        val responseText = "Задача ${job.id}: ${job.status.labelRu()}"

        commandGateway.logCommand(
            command = command,
            args = args,
            chatId = message.chat.id,
            userId = message.from?.id,
            username = message.from?.username,
            status = status,
            errorMessage = if (isSuccess) null else job.logText.takeLast(300).ifBlank { job.status.name },
            responseText = responseText,
            executionTimeMs = System.currentTimeMillis() - startTime,
        )

        if (!isSuccess) {
            commandGateway.sendMessage(
                chatId = message.chat.id,
                text = buildString {
                    append("Задача <code>${job.id}</code> завершилась со статусом: <b>${job.status.labelRu()}</b>.")
                    if (job.logText.isNotBlank()) {
                        append("\n${job.logText.takeLast(500).escapeHtml()}")
                    }
                },
                replyToMessageId = message.messageId,
            )
        }
    }

    private class CommandExecutableToolHandler(
        override val toolId: String,
        private val commandExecutable: CommandExecutable,
        private val message: Message,
        private val args: String?,
    ) : ToolHandler {
        override suspend fun execute(job: ToolJob, progress: ToolJobProgressSink): ToolJobResult {
            progress.updateProgress(5, "Запуск /${commandExecutable.command}")
            commandExecutable.execute(message, args)
            progress.updateProgress(100, "Команда /${commandExecutable.command} завершена")
            return ToolJobResult(
                outputJson = """{"command":"${commandExecutable.command}","chatId":${message.chat.id}}""",
                logText = "Telegram-обработчик завершил выполнение",
            )
        }
    }

    private fun ToolJobStatus.labelRu(): String = when (this) {
        ToolJobStatus.QUEUED -> "в очереди"
        ToolJobStatus.RUNNING -> "выполняется"
        ToolJobStatus.WAITING_FOR_CONFIRMATION -> "ждет подтверждения"
        ToolJobStatus.SUCCESS -> "успешно"
        ToolJobStatus.FAILED -> "ошибка"
        ToolJobStatus.CANCELLED -> "отменено"
        ToolJobStatus.BLOCKED -> "заблокировано"
    }

    private fun buildCommandInputJson(command: String, args: String?, message: Message): String {
        return buildString {
            append("{")
            appendJsonField("command", command)
            append(",")
            appendJsonField("args", args)
            append(",\"chatId\":${message.chat.id}")
            append(",\"userId\":${message.from?.id ?: "null"}")
            append("}")
        }
    }

    private fun buildBlockedPayloadJson(
        command: String,
        args: String?,
        message: Message,
        decision: CommandSafetyDecision,
    ): String {
        return buildString {
            append("{")
            appendJsonField("command", command)
            append(",")
            appendJsonField("args", args)
            append(",")
            appendJsonField("riskTier", decision.capability?.riskTier?.name)
            append(",")
            appendJsonField("reason", decision.reason?.name)
            append(",")
            appendJsonField(
                "missingPermissions",
                decision.missingPermissions.joinToString(",") { it.permission }.ifBlank { null },
            )
            append(",\"chatId\":${message.chat.id}")
            append(",\"userId\":${message.from?.id ?: "null"}")
            append("}")
        }
    }

    private fun StringBuilder.appendJsonField(name: String, value: String?) {
        append("\"")
        append(name)
        append("\":")
        if (value == null) {
            append("null")
        } else {
            append("\"")
            append(value.replace("\\", "\\\\").replace("\"", "\\\""))
            append("\"")
        }
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    fun getAvailableCommands(): List<Pair<String, String>> {
        return handlers.map { (name, handler) ->
            name to handler.description
        }
    }
}
