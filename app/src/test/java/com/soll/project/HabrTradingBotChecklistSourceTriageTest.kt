package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HabrTradingBotChecklistSourceTriageTest {
    @Test
    fun `trading bot checklist is mapped to Soll without inventing trading scope`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val knowledge = projectFile(
            "docs/knowledge/trading-bot-launch-checklist-soll-app.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-854a3820824d-17b09ac75ba609e6-verification.md",
        ).readText()

        listOf(
            "source-item/854a3820824d/17b09ac75ba609e6",
            "docs/knowledge/trading-bot-launch-checklist-soll-app.md",
            "zero measured trading runtime value",
            "stale `RUNNING`",
            "end-to-end idempotency",
        ).forEach { decision ->
            assertTrue("Missing trading-checklist roadmap decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "Path-aware exits -> verify the real effect path",
            "Look-ahead bias -> enforce an explicit as-of boundary",
            "Fees, slippage and leverage -> count real execution economics",
            "Sample size -> show `N/A`, uncertainty and promotion gates",
            "Crash recovery -> durable state plus deterministic reconciliation",
            "Exchange adapter -> distinguish accepted, executed and persisted",
            "Ten strategies, one account -> enforce aggregate exposure budgets",
            "External data and cron -> replay on the simulation clock",
            "BotService` is an archived placeholder",
            "event.createdAt < forecastStartMillis",
            "SyncQueueDao.getReadyItems()",
            "temporary path, close and validate length/hash",
            "operation IDs/idempotency keys",
            "zero measured",
        ).forEach { control ->
            assertTrue("Missing adapted checklist control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: checklist_adapted_to_soll_app_knowledge_base",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-854a3820824d-17b09ac75ba609e6-verification.md",
            "8 checklist risks mapped",
            "3 concrete durability gaps identified",
            "0 measured trading runtime value",
            "HabrTradingBotChecklistSourceTriageTest",
        ).forEach { evidence ->
            assertTrue("Missing checklist verification evidence: $evidence", verification.contains(evidence))
        }
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }
}
