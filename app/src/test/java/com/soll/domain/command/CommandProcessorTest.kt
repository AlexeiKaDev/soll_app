package com.soll.domain.command

import com.soll.data.api.model.Chat
import com.soll.data.api.model.Message
import com.soll.data.api.model.User
import com.soll.data.local.entity.CommandLogEntity
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.assistant.Capability
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.assistant.CapabilitySettings
import com.soll.domain.command.AllowAllCapabilityPermissionChecker
import com.soll.domain.command.CapabilityPermissionChecker
import com.soll.domain.command.CommandSafetyGate
import com.soll.domain.command.MissingCapabilityPermission
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobRunner
import com.soll.domain.tool.ToolJobStatus
import com.soll.domain.tool.ToolJobStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandProcessorTest {
    @Test
    fun `allowed command executes handler and logs success`() = runBlocking {
        val gateway = FakeCommandGateway()
        val handler = FakeCommandExecutable(command = "ping")
        val processor = processor(
            gateway = gateway,
            handlers = mapOf("ping" to handler),
        )

        processor.processCommand("ping", args = null, message = testMessage())

        assertEquals(1, handler.executionCount)
        assertEquals(CommandLogEntity.STATUS_SUCCESS, gateway.commandLogs.single().status)
        assertEquals("ping", gateway.commandLogs.single().command)
    }

    @Test
    fun `disabled command does not execute handler and writes audit event`() = runBlocking {
        val gateway = FakeCommandGateway()
        val eventLogger = FakeAssistantEventLogger()
        val handler = FakeCommandExecutable(command = "photo")
        val processor = processor(
            gateway = gateway,
            eventLogger = eventLogger,
            settings = FakeCapabilitySettings(disabledCapabilities = setOf("photo")),
            handlers = mapOf("photo" to handler),
        )

        processor.processCommand("photo", args = "front", message = testMessage())

        assertEquals(0, handler.executionCount)
        assertTrue(gateway.sentMessages.single().text.contains("Команда /photo заблокирована"))
        assertEquals(CommandLogEntity.STATUS_ERROR, gateway.commandLogs.single().status)
        assertTrue(gateway.commandLogs.single().errorMessage!!.contains("отключена"))
        assertEquals("capability_blocked", eventLogger.events.single().type)
        assertTrue(eventLogger.events.single().payloadJson!!.contains("\"command\":\"photo\""))
    }

    @Test
    fun `job backed command creates tool job and logs command success`() = runBlocking {
        val gateway = FakeCommandGateway()
        val store = FakeToolJobStore()
        val handler = FakeCommandExecutable(command = "photo")
        val processor = processor(
            gateway = gateway,
            handlers = mapOf("photo" to handler),
            toolJobRunner = ToolJobRunner(store),
            toolJobStore = store,
        )

        processor.processCommand("photo", args = "back --confirm", message = testMessage())

        assertEquals(1, handler.executionCount)
        assertEquals("back", handler.lastArgs)
        assertEquals(ToolJobStatus.SUCCESS, store.jobs.values.single().status)
        assertTrue(gateway.sentMessages.first().text.contains("Создана задача инструмента"))
        assertEquals(CommandLogEntity.STATUS_SUCCESS, gateway.commandLogs.single().status)
    }

    @Test
    fun `risky command without confirm is blocked before handler`() = runBlocking {
        val gateway = FakeCommandGateway()
        val eventLogger = FakeAssistantEventLogger()
        val handler = FakeCommandExecutable(command = "sms_send")
        val processor = processor(
            gateway = gateway,
            eventLogger = eventLogger,
            handlers = mapOf("sms_send" to handler),
        )

        processor.processCommand("sms_send", args = "+1234567890 text", message = testMessage())

        assertEquals(0, handler.executionCount)
        assertTrue(gateway.sentMessages.single().text.contains("--confirm"))
        assertEquals(CommandLogEntity.STATUS_ERROR, gateway.commandLogs.single().status)
        assertEquals("command_safety_blocked", eventLogger.events.single().type)
        assertTrue(eventLogger.events.single().payloadJson!!.contains("CONFIRMATION_REQUIRED"))
    }

    @Test
    fun `risky command with confirm strips confirm flag before handler`() = runBlocking {
        val handler = FakeCommandExecutable(command = "sms_send")
        val processor = processor(
            handlers = mapOf("sms_send" to handler),
        )

        processor.processCommand("sms_send", args = "+1234567890 текст --confirm", message = testMessage())

        assertEquals(1, handler.executionCount)
        assertEquals("+1234567890 текст", handler.lastArgs)
    }

    @Test
    fun `missing android permission blocks command before confirmation`() = runBlocking {
        val gateway = FakeCommandGateway()
        val eventLogger = FakeAssistantEventLogger()
        val handler = FakeCommandExecutable(command = "sms_send")
        val registry = CapabilityRegistry(FakeCapabilitySettings())
        val processor = processor(
            gateway = gateway,
            eventLogger = eventLogger,
            capabilityRegistry = registry,
            commandSafetyGate = CommandSafetyGate(
                capabilityRegistry = registry,
                permissionChecker = FakePermissionChecker(
                    missing = listOf(MissingCapabilityPermission("android.permission.SEND_SMS", "отправка SMS")),
                ),
            ),
            handlers = mapOf("sms_send" to handler),
        )

        processor.processCommand("sms_send", args = "+1234567890 текст --confirm", message = testMessage())

        assertEquals(0, handler.executionCount)
        assertTrue(gateway.sentMessages.single().text.contains("отправка SMS"))
        assertEquals("command_safety_blocked", eventLogger.events.single().type)
        assertTrue(eventLogger.events.single().payloadJson!!.contains("MISSING_PERMISSION"))
    }

    @Test
    fun `unknown command keeps existing unknown command behavior`() = runBlocking {
        val gateway = FakeCommandGateway()
        val eventLogger = FakeAssistantEventLogger()
        val processor = processor(
            gateway = gateway,
            eventLogger = eventLogger,
            handlers = mapOf("ping" to FakeCommandExecutable(command = "ping")),
        )

        processor.processCommand("missing", args = null, message = testMessage())

        assertTrue(gateway.sentMessages.single().text.contains("Неизвестная команда"))
        assertEquals(CommandLogEntity.STATUS_ERROR, gateway.commandLogs.single().status)
        assertEquals("Неизвестная команда", gateway.commandLogs.single().errorMessage)
        assertTrue(eventLogger.events.isEmpty())
    }

    private fun processor(
        gateway: FakeCommandGateway = FakeCommandGateway(),
        eventLogger: FakeAssistantEventLogger = FakeAssistantEventLogger(),
        settings: FakeCapabilitySettings = FakeCapabilitySettings(),
        capabilityRegistry: CapabilityRegistry = CapabilityRegistry(settings),
        commandSafetyGate: CommandSafetyGate = CommandSafetyGate(
            capabilityRegistry,
            AllowAllCapabilityPermissionChecker,
        ),
        handlers: Map<String, CommandExecutable>,
        toolJobRunner: ToolJobRunner? = null,
        toolJobStore: ToolJobStore? = null,
    ): CommandProcessor = CommandProcessor(
        commandGateway = gateway,
        capabilityRegistry = capabilityRegistry,
        commandSafetyGate = commandSafetyGate,
        assistantEventLogger = eventLogger,
        handlers = handlers,
        toolJobRunner = toolJobRunner,
        toolJobStore = toolJobStore,
    )

    private fun testMessage(): Message = Message(
        messageId = 10L,
        from = User(
            id = 20L,
            isBot = false,
            firstName = "Tester",
            username = "tester",
        ),
        chat = Chat(id = 30L, type = "private", firstName = "Tester"),
        date = System.currentTimeMillis() / 1000,
        text = "/ping",
    )

    private class FakeCommandExecutable(
        override val command: String,
        override val description: String = "Fake command",
    ) : CommandExecutable {
        var executionCount: Int = 0
        var lastArgs: String? = null

        override suspend fun execute(message: Message, args: String?) {
            executionCount++
            lastArgs = args
        }
    }

    private class FakePermissionChecker(
        private val missing: List<MissingCapabilityPermission>,
    ) : CapabilityPermissionChecker {
        override fun missingPermissions(capability: Capability): List<MissingCapabilityPermission> = missing
    }

    private class FakeCapabilitySettings(
        private val riskyCapabilitiesEnabled: Boolean = true,
        private val disabledCapabilities: Set<String> = emptySet(),
    ) : CapabilitySettings {
        override fun isRiskyCapabilitiesEnabled(): Boolean = riskyCapabilitiesEnabled

        override fun isCapabilityEnabled(capability: Capability): Boolean =
            capability.enabledByDefault && capability.id !in disabledCapabilities
    }

    private class FakeAssistantEventLogger : AssistantEventLogger {
        val events = mutableListOf<AssistantEvent>()

        override suspend fun logEvent(event: AssistantEvent) {
            events += event
        }
    }

    private class FakeToolJobStore : ToolJobStore {
        val jobs = linkedMapOf<String, ToolJob>()

        override fun getRecentJobs(limit: Int): Flow<List<ToolJob>> =
            flowOf(jobs.values.take(limit))

        override fun getJobsByStatus(status: ToolJobStatus): Flow<List<ToolJob>> =
            flowOf(jobs.values.filter { it.status == status })

        override suspend fun getJob(id: String): ToolJob? =
            jobs[id]

        override suspend fun countActiveJobs(): Int =
            jobs.values.count {
                it.status == ToolJobStatus.QUEUED ||
                    it.status == ToolJobStatus.RUNNING ||
                    it.status == ToolJobStatus.WAITING_FOR_CONFIRMATION
            }

        override suspend fun insert(job: ToolJob): ToolJob {
            jobs[job.id] = job
            return job
        }

        override suspend fun update(job: ToolJob) {
            jobs[job.id] = job
        }

        override suspend fun deleteFinishedJobs() {
            jobs.entries.removeIf { it.value.finishedAt != null }
        }
    }

    private data class SentMessage(
        val chatId: Long,
        val text: String,
    )

    private data class CommandLog(
        val command: String,
        val status: String,
        val errorMessage: String?,
    )

    private class FakeCommandGateway : CommandExecutionGateway {
        val sentMessages = mutableListOf<SentMessage>()
        val commandLogs = mutableListOf<CommandLog>()

        override suspend fun sendMessage(
            chatId: Long,
            text: String,
            parseMode: String?,
            replyToMessageId: Long?,
        ): Result<Message> {
            sentMessages += SentMessage(chatId, text)
            return Result.success(
                Message(
                    messageId = 1L,
                    chat = Chat(id = chatId, type = "private"),
                    date = System.currentTimeMillis() / 1000,
                    text = text,
                )
            )
        }

        override suspend fun logCommand(
            command: String,
            args: String?,
            chatId: Long,
            userId: Long?,
            username: String?,
            status: String,
            errorMessage: String?,
            responseText: String?,
            executionTimeMs: Long?,
        ) {
            commandLogs += CommandLog(
                command = command,
                status = status,
                errorMessage = errorMessage,
            )
        }
    }
}
