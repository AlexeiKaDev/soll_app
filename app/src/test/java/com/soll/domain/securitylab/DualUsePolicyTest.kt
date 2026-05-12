package com.soll.domain.securitylab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualUsePolicyTest {
    @Test
    fun `owned lab note is allowed only after confirmation`() {
        val decision = DualUsePolicy.review(
            DualUsePolicyRequest(
                title = "ESP32 стенд",
                objective = "Инвентаризация GPIO и UART для своего устройства",
                activity = SecurityLabActivity.NOTE_TEMPLATE,
                ownership = SecurityLabOwnership.OWNED_LAB,
            ),
        )

        assertTrue(decision.allowed)
        assertTrue(decision.requiresConfirmation)
        assertEquals(DualUsePolicyOutcome.CONFIRMATION_REQUIRED, decision.outcome)
        assertTrue(decision.requiredConfirmations.any { it.contains("мой стенд") })
    }

    @Test
    fun `policy blocks payload storage and execution`() {
        val storage = DualUsePolicy.review(
            DualUsePolicyRequest(
                title = "BadUSB коллекция",
                objective = "Сохранить payload",
                activity = SecurityLabActivity.PAYLOAD_STORAGE,
                ownership = SecurityLabOwnership.OWNED_LAB,
            ),
        )
        val execution = DualUsePolicy.review(
            DualUsePolicyRequest(
                title = "Запуск сценария",
                objective = "Выполнить ducky script",
                activity = SecurityLabActivity.PAYLOAD_EXECUTION,
                ownership = SecurityLabOwnership.OWNED_LAB,
            ),
        )

        assertFalse(storage.allowed)
        assertFalse(execution.allowed)
        assertEquals(DualUsePolicyOutcome.BLOCKED, storage.outcome)
        assertEquals(DualUsePolicyOutcome.BLOCKED, execution.outcome)
    }

    @Test
    fun `policy blocks apartment key clone topics`() {
        val analysis = DualUseSourceAnalyzer.analyze(
            title = "NFC ключ подъезда",
            text = "как скопировать ключ домофона",
        )
        val decision = DualUsePolicy.review(
            DualUsePolicyRequest(
                title = "NFC ключ подъезда",
                objective = "Диагностика копирования",
                activity = SecurityLabActivity.SOURCE_ANALYSIS,
                ownership = SecurityLabOwnership.OWNED_LAB,
                sourceAnalysis = analysis,
            ),
        )

        assertEquals(DualUseTopic.CREDENTIAL_OR_ACCESS, analysis.dualUseTopic)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("заблокирована"))
    }

    @Test
    fun `nfc ndef write to owned tag requires confirmation but is not blocked`() {
        val decision = DualUsePolicy.review(
            DualUsePolicyRequest(
                title = "NFC NDEF запись",
                objective = "Записать обычный текст на свою NFC Forum метку",
                activity = SecurityLabActivity.SOURCE_ANALYSIS,
                ownership = SecurityLabOwnership.OWNED_LAB,
            ),
        )

        assertTrue(decision.allowed)
        assertTrue(decision.requiresConfirmation)
        assertTrue(decision.requiredConfirmations.any { it.contains("мой стенд") })
    }

    @Test
    fun `policy blocks unknown or third party targets`() {
        val decision = DualUsePolicy.review(
            DualUsePolicyRequest(
                title = "Чужое устройство",
                objective = "Посмотреть интерфейсы без разрешения",
                activity = SecurityLabActivity.NOTE_TEMPLATE,
                ownership = SecurityLabOwnership.UNKNOWN,
            ),
        )

        assertFalse(decision.allowed)
        assertEquals(DualUsePolicyOutcome.BLOCKED, decision.outcome)
    }

    @Test
    fun `rf checklist template is safe note with dual use topic`() {
        val note = SecurityLabTemplates.rfLegalChecklist(
            SecurityLabNoteContext(
                title = "433 MHz датчик",
                target = "мой датчик на тестовом стенде",
                scope = "только документация и пассивная инвентаризация",
                owner = "личный стенд",
                dualUseTopic = DualUseTopic.RF_LEGAL_CHECKLIST,
            ),
        )

        assertTrue(note.contains("dual_use_topic: rf_legal_checklist"))
        assertTrue(note.contains("owned_lab"))
        assertTrue(note.contains("Нет вмешательства"))
        assertFalse(note.contains("BadUSB"))
    }

    @Test
    fun `audit event stores summary without raw payload`() {
        val request = DualUsePolicyRequest(
            title = "ESP32 стенд",
            objective = "Threat model своего устройства",
            activity = SecurityLabActivity.THREAT_MODEL,
            ownership = SecurityLabOwnership.EXPLICIT_PERMISSION,
        )
        val decision = DualUsePolicy.review(request)
        val event = SecurityLabAudit.eventFor(request, decision)

        assertEquals("security_lab.policy", event.source)
        assertTrue(event.type.startsWith("security_lab_"))
        assertNull(event.payloadJson)
    }
}
