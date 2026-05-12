package com.soll.domain.securitylab

import com.soll.domain.assistant.AssistantEvent

enum class DualUseTopic(val wireName: String) {
    NONE("none"),
    SAFE_HARDWARE("safe_hardware"),
    NFC_RFID("nfc_rfid"),
    RF_LEGAL_CHECKLIST("rf_legal_checklist"),
    CREDENTIAL_OR_ACCESS("credential_or_access"),
    PAYLOAD_REPOSITORY("payload_repository"),
    ACCESS_BYPASS("access_bypass"),
    PUBLIC_DISRUPTION("public_disruption"),
    THIRD_PARTY_TARGET("third_party_target"),
}

enum class SecurityLabActivity {
    NOTE_TEMPLATE,
    SOURCE_ANALYSIS,
    THREAT_MODEL,
    HARDENING_NOTE,
    RF_LEGAL_CHECKLIST,
    PAYLOAD_STORAGE,
    PAYLOAD_EXECUTION,
    CREDENTIAL_OR_KEY_DUMP,
    ACCESS_BYPASS,
    RF_TRANSMISSION,
    PUBLIC_DISRUPTION,
}

enum class SecurityLabOwnership {
    OWNED_LAB,
    EXPLICIT_PERMISSION,
    UNKNOWN,
    THIRD_PARTY,
}

enum class DualUsePolicyOutcome {
    ALLOWED,
    CONFIRMATION_REQUIRED,
    BLOCKED,
}

data class DualUseSourceAnalysis(
    val dualUseTopic: DualUseTopic,
    val reason: String,
)

data class DualUsePolicyRequest(
    val title: String,
    val objective: String,
    val activity: SecurityLabActivity,
    val ownership: SecurityLabOwnership,
    val sourceAnalysis: DualUseSourceAnalysis = DualUseSourceAnalyzer.analyze(title, objective),
)

data class DualUsePolicyDecision(
    val outcome: DualUsePolicyOutcome,
    val reason: String,
    val requiredConfirmations: List<String> = emptyList(),
) {
    val allowed: Boolean = outcome != DualUsePolicyOutcome.BLOCKED
    val requiresConfirmation: Boolean = outcome == DualUsePolicyOutcome.CONFIRMATION_REQUIRED
}

object DualUseSourceAnalyzer {
    fun analyze(title: String, text: String): DualUseSourceAnalysis {
        val normalized = "$title $text".lowercase()
        return when {
            normalized.anyContains(PAYLOAD_TERMS) -> DualUseSourceAnalysis(
                dualUseTopic = DualUseTopic.PAYLOAD_REPOSITORY,
                reason = "Источник похож на коллекцию payload или сценариев выполнения.",
            )

            normalized.anyContains(CREDENTIAL_TERMS) -> DualUseSourceAnalysis(
                dualUseTopic = DualUseTopic.CREDENTIAL_OR_ACCESS,
                reason = "Источник затрагивает учетные данные, карты доступа или ключи.",
            )

            normalized.anyContains(BYPASS_TERMS) -> DualUseSourceAnalysis(
                dualUseTopic = DualUseTopic.ACCESS_BYPASS,
                reason = "Источник похож на обход доступа или защит.",
            )

            normalized.anyContains(DISRUPTION_TERMS) -> DualUseSourceAnalysis(
                dualUseTopic = DualUseTopic.PUBLIC_DISRUPTION,
                reason = "Источник похож на сценарии нарушения связи или работы чужих систем.",
            )

            normalized.anyContains(NFC_RFID_TERMS) -> DualUseSourceAnalysis(
                dualUseTopic = DualUseTopic.NFC_RFID,
                reason = "Источник относится к NFC/RFID и требует owned-lab режима.",
            )

            normalized.anyContains(RF_TERMS) -> DualUseSourceAnalysis(
                dualUseTopic = DualUseTopic.RF_LEGAL_CHECKLIST,
                reason = "Источник относится к радиоинтерфейсам и требует юридического чеклиста.",
            )

            normalized.anyContains(HARDWARE_TERMS) -> DualUseSourceAnalysis(
                dualUseTopic = DualUseTopic.SAFE_HARDWARE,
                reason = "Источник похож на безопасную hardware-заметку.",
            )

            else -> DualUseSourceAnalysis(
                dualUseTopic = DualUseTopic.NONE,
                reason = "Dual-use тема не обнаружена.",
            )
        }
    }

    private fun String.anyContains(terms: Set<String>): Boolean =
        terms.any { contains(it) }

    private val PAYLOAD_TERMS = setOf(
        "payload",
        "badusb",
        "rubber ducky",
        "ducky script",
        "скрипт badusb",
    )

    private val CREDENTIAL_TERMS = setOf(
        "credential",
        "card dump",
        "key dump",
        "mifare key",
        "ключ подъезда",
        "домофон",
        "дамп карты",
        "копия ключа",
        "скопировать ключ",
        "клонировать ключ",
    )

    private val BYPASS_TERMS = setOf(
        "bypass",
        "обход",
        "обойти",
        "взломать",
        "unlock without permission",
    )

    private val DISRUPTION_TERMS = setOf(
        "deauth",
        "jamming",
        "jammer",
        "глушение",
        "глушилка",
        "disruption",
    )

    private val NFC_RFID_TERMS = setOf(
        "nfc",
        "rfid",
        "mifare",
        "ntag",
        "ndef",
    )

    private val RF_TERMS = setOf(
        "sub-ghz",
        "subghz",
        "rf ",
        "433",
        "868",
        "915",
        "радио",
        "радиочастот",
    )

    private val HARDWARE_TERMS = setOf(
        "gpio",
        "uart",
        "i2c",
        "spi",
        "esp32",
        "flipper",
        "ir",
    )
}

object DualUsePolicy {
    fun review(request: DualUsePolicyRequest): DualUsePolicyDecision {
        if (request.activity in BLOCKED_ACTIVITIES) {
            return DualUsePolicyDecision(
                outcome = DualUsePolicyOutcome.BLOCKED,
                reason = "Это действие запрещено: приложение не хранит, не запускает и не помогает исполнять payload, дампы ключей или обходы доступа.",
            )
        }

        if (request.sourceAnalysis.dualUseTopic in BLOCKED_TOPICS) {
            return DualUsePolicyDecision(
                outcome = DualUsePolicyOutcome.BLOCKED,
                reason = "Тема заблокирована политикой безопасности: ${request.sourceAnalysis.reason}",
            )
        }

        if (request.activity == SecurityLabActivity.RF_TRANSMISSION) {
            return DualUsePolicyDecision(
                outcome = DualUsePolicyOutcome.BLOCKED,
                reason = "Передача RF-сигналов из приложения не поддерживается. Разрешены только заметки, инвентаризация и юридический чеклист.",
            )
        }

        if (request.ownership !in ALLOWED_OWNERSHIP) {
            return DualUsePolicyDecision(
                outcome = DualUsePolicyOutcome.BLOCKED,
                reason = "Security Lab работает только для своего стенда или явно разрешенной лаборатории.",
            )
        }

        return DualUsePolicyDecision(
            outcome = DualUsePolicyOutcome.CONFIRMATION_REQUIRED,
            reason = "Можно сохранить только безопасную заметку после подтверждения owned-lab режима.",
            requiredConfirmations = listOf(
                "Это мой стенд или у меня есть явное разрешение.",
                "Заметка не содержит дампов ключей, инструкций обхода доступа или сценариев выполнения.",
                "Для RF/NFC работ я проверил законность, частоты и правила площадки.",
            ),
        )
    }

    private val BLOCKED_ACTIVITIES = setOf(
        SecurityLabActivity.PAYLOAD_STORAGE,
        SecurityLabActivity.PAYLOAD_EXECUTION,
        SecurityLabActivity.CREDENTIAL_OR_KEY_DUMP,
        SecurityLabActivity.ACCESS_BYPASS,
        SecurityLabActivity.PUBLIC_DISRUPTION,
    )

    private val BLOCKED_TOPICS = setOf(
        DualUseTopic.PAYLOAD_REPOSITORY,
        DualUseTopic.CREDENTIAL_OR_ACCESS,
        DualUseTopic.ACCESS_BYPASS,
        DualUseTopic.PUBLIC_DISRUPTION,
        DualUseTopic.THIRD_PARTY_TARGET,
    )

    private val ALLOWED_OWNERSHIP = setOf(
        SecurityLabOwnership.OWNED_LAB,
        SecurityLabOwnership.EXPLICIT_PERMISSION,
    )
}

data class SecurityLabNoteContext(
    val title: String,
    val target: String,
    val scope: String,
    val owner: String,
    val dualUseTopic: DualUseTopic,
)

object SecurityLabTemplates {
    fun ownedLabNote(context: SecurityLabNoteContext): String = """
        |# ${context.title}
        |
        |dual_use_topic: ${context.dualUseTopic.wireName}
        |режим: owned_lab
        |объект: ${context.target}
        |владелец/разрешение: ${context.owner}
        |границы работ: ${context.scope}
        |
        |## Цель
        |- Зафиксировать наблюдения, схему подключения, версии прошивок и безопасные выводы.
        |
        |## Разрешено
        |- Инвентаризация интерфейсов и документации.
        |- Threat model для своего стенда.
        |- Hardening-заметки и ссылки на официальные источники.
        |
        |## Запрещено
        |- Дампы ключей или учетных данных.
        |- Инструкции обхода доступа.
        |- Действия против чужих систем или публичной связи.
    """.trimMargin()

    fun rfLegalChecklist(context: SecurityLabNoteContext): String = """
        |# RF/legal чеклист: ${context.title}
        |
        |dual_use_topic: ${DualUseTopic.RF_LEGAL_CHECKLIST.wireName}
        |режим: owned_lab
        |объект: ${context.target}
        |
        |- Подтверждено, что устройство и площадка принадлежат мне или есть явное разрешение.
        |- Проверены разрешенные частоты, мощность, duty cycle и местные правила.
        |- Работа ограничена пассивной инвентаризацией, заметками и hardening-выводами.
        |- Нет вмешательства в чужие устройства, сети, домофоны, автомобили или публичную связь.
        |- Результат сохраняется как безопасная заметка без дампов, секретов и обходов.
    """.trimMargin()

    fun threatModel(context: SecurityLabNoteContext): String = """
        |# Threat model: ${context.title}
        |
        |dual_use_topic: ${context.dualUseTopic.wireName}
        |режим: owned_lab
        |объект: ${context.target}
        |границы работ: ${context.scope}
        |
        |## Активы
        |- Устройства, данные и доступы внутри разрешенного стенда.
        |
        |## Риски
        |- Неправильная конфигурация.
        |- Слабая аутентификация.
        |- Отсутствие логирования или обновлений.
        |
        |## Защита
        |- Минимальные права.
        |- Обновления.
        |- Резервные копии.
        |- Журналирование действий.
    """.trimMargin()
}

object SecurityLabAudit {
    fun eventFor(request: DualUsePolicyRequest, decision: DualUsePolicyDecision): AssistantEvent =
        AssistantEvent(
            type = when (decision.outcome) {
                DualUsePolicyOutcome.ALLOWED -> "security_lab_allowed"
                DualUsePolicyOutcome.CONFIRMATION_REQUIRED -> "security_lab_confirmation_required"
                DualUsePolicyOutcome.BLOCKED -> "security_lab_blocked"
            },
            source = "security_lab.policy",
            summary = "${request.activity.name.lowercase()}: ${decision.reason}",
            payloadJson = null,
        )
}
