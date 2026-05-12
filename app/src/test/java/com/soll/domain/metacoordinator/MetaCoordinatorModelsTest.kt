package com.soll.domain.metacoordinator

import com.soll.domain.assistant.Capability
import com.soll.domain.assistant.CapabilityBlockReason
import com.soll.domain.assistant.CapabilityDecision
import com.soll.domain.assistant.RiskTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaCoordinatorModelsTest {
    @Test
    fun `request sent to server excludes private context`() {
        val request = MetaCoordinatorRequest(
            userRequestSummary = "помоги с задачами",
            allowedCapabilities = emptyList(),
            context = listOf(
                MetaCoordinatorContextItem("screen", "home", private = false),
                MetaCoordinatorContextItem("telegram_text", "личный текст", private = true),
            ),
        )

        val safe = request.safeForServer()

        assertEquals(1, safe.context.size)
        assertEquals("screen", safe.context.single().key)
    }

    @Test
    fun `suggested risky action requires confirmation before execution`() {
        val action = MetaSuggestedAction(
            id = "send_raw",
            capabilityId = "raw",
            title = "Сохранить заметку",
            summary = "Создать raw-заметку",
            riskTier = RiskTier.MONEY_OR_EXTERNAL_ACTION,
            requiresConfirmation = true,
        )
        val decision = MetaCoordinatorActionGate.review(
            action = action,
            capabilityDecision = CapabilityDecision(
                allowed = true,
                capability = capability("raw", RiskTier.MONEY_OR_EXTERNAL_ACTION, requiresConfirmation = false),
                message = "Разрешено",
            ),
            userConfirmed = false,
        )

        assertFalse(decision.allowed)
        assertTrue(decision.requiresConfirmation)
        assertEquals(MetaSuggestedActionStatus.CONFIRMATION_REQUIRED, decision.status)
    }

    @Test
    fun `confirmed suggested action is allowed after capability check`() {
        val action = MetaSuggestedAction(
            id = "open_logs",
            capabilityId = "logs",
            title = "Открыть логи",
            summary = "Показать журнал",
            riskTier = RiskTier.SAFE_INFO,
            requiresConfirmation = false,
        )
        val decision = MetaCoordinatorActionGate.review(
            action = action,
            capabilityDecision = CapabilityDecision(
                allowed = true,
                capability = capability("logs", RiskTier.SAFE_INFO, requiresConfirmation = false),
                message = "Разрешено",
            ),
            userConfirmed = false,
        )

        assertTrue(decision.allowed)
        assertEquals(MetaSuggestedActionStatus.ALLOWED, decision.status)
    }

    @Test
    fun `blocked capability blocks server suggested action`() {
        val action = MetaSuggestedAction(
            id = "nfc_write",
            capabilityId = "nfc",
            title = "Записать NFC",
            summary = "Запись метки",
            riskTier = RiskTier.DUAL_USE_HARDWARE,
            requiresConfirmation = true,
        )
        val decision = MetaCoordinatorActionGate.review(
            action = action,
            capabilityDecision = CapabilityDecision(
                allowed = false,
                capability = capability("nfc", RiskTier.DUAL_USE_HARDWARE, requiresConfirmation = true),
                reason = CapabilityBlockReason.CAPABILITY_DISABLED,
                message = "NFC отключен в настройках.",
            ),
            userConfirmed = true,
        )

        assertFalse(decision.allowed)
        assertEquals(MetaSuggestedActionStatus.BLOCKED, decision.status)
        assertTrue(decision.reason.contains("отключен"))
    }

    @Test
    fun `fallback response has no suggested actions and logs decision chain`() {
        val response = MetaCoordinatorFallback.unavailable(
            request = MetaCoordinatorRequest(
                userRequestSummary = "что делать дальше",
                allowedCapabilities = emptyList(),
            ),
            reason = "timeout",
        )
        val markdown = MetaDecisionChainLog.toLocalMarkdown(response)

        assertFalse(response.serverAvailable)
        assertTrue(response.suggestedActions.isEmpty())
        assertTrue(response.answer.contains("недоступен"))
        assertTrue(markdown.contains("fallback"))
        assertTrue(markdown.contains("timeout"))
    }

    @Test
    fun `server bridge question excludes private context`() {
        val question = MetaCoordinatorServerBridge.toAssistantQuestion(
            MetaCoordinatorRequest(
                userRequestSummary = "что делать дальше",
                allowedCapabilities = listOf(
                    MetaAllowedCapability(
                        id = "logs",
                        name = "Логи",
                        riskTier = RiskTier.SAFE_INFO,
                        requiresConfirmation = false,
                    ),
                ),
                context = listOf(
                    MetaCoordinatorContextItem("screen", "home", private = false),
                    MetaCoordinatorContextItem("raw_telegram", "секретный текст", private = true),
                ),
            )
        )

        assertTrue(question.contains("что делать дальше"))
        assertTrue(question.contains("logs"))
        assertTrue(question.contains("screen"))
        assertFalse(question.contains("секретный текст"))
    }

    @Test
    fun `server bridge maps assistant answer into meta response`() {
        val response = MetaCoordinatorServerBridge.fromAssistantAnswer(
            request = MetaCoordinatorRequest(
                userRequestSummary = "проверь задачи",
                allowedCapabilities = emptyList(),
            ),
            answer = "Сначала открой задачи на сегодня.",
            usedTopics = listOf("task-board"),
            confidence = "high",
        )

        assertTrue(response.serverAvailable)
        assertEquals("Сначала открой задачи на сегодня.", response.answer)
        assertTrue(response.suggestedActions.isEmpty())
        assertTrue(response.decisionChain.any { it.type == MetaDecisionStepType.SERVER_RESPONSE })
    }

    @Test
    fun `audit event does not store raw payload json`() {
        val response = MetaCoordinatorResponse(
            answer = "Открою локальный статус после подтверждения.",
            suggestedActions = listOf(
                MetaSuggestedAction(
                    id = "status",
                    capabilityId = "status",
                    title = "Статус",
                    summary = "Показать статус",
                    riskTier = RiskTier.SAFE_INFO,
                    requiresConfirmation = false,
                    payloadPreview = "raw private payload",
                ),
            ),
        )
        val event = MetaCoordinatorAudit.eventFor(response)

        assertEquals("meta_coordinator", event.source)
        assertNull(event.payloadJson)
        assertFalse(event.summary.contains("raw private payload"))
    }

    private fun capability(
        id: String,
        riskTier: RiskTier,
        requiresConfirmation: Boolean,
    ): Capability = Capability(
        id = id,
        name = id,
        description = id,
        riskTier = riskTier,
        requiredAndroidPermissions = emptyList(),
        requiresConfirmation = requiresConfirmation,
        enabledByDefault = true,
        auditRequired = riskTier != RiskTier.SAFE_INFO,
    )
}
