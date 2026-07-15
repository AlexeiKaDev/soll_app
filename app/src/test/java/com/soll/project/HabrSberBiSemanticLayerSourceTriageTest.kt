package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HabrSberBiSemanticLayerSourceTriageTest {
    @Test
    fun `BI article is captured and semantic layer adoption stays report gated`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val knowledge = projectFile(
            "docs/knowledge/bi-semantic-layer-financial-reporting.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-94b02ac6da81-43712e77076a40a8-verification.md",
        ).readText()

        listOf(
            "source-item/94b02ac6da81/43712e77076a40a8",
            "docs/knowledge/bi-semantic-layer-financial-reporting.md",
            "0 current financial reports",
            "6 adoption gates",
            "0 measured report-automation value",
        ).forEach { decision ->
            assertTrue("Missing BI roadmap decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "OLAP-модель",
            "web / Excel / LibreOffice",
            "Что должен владеть семантический слой",
            "Шаблон паспорта показателя",
            "Оценка текущего `soll_app`",
            "0 current financial reports",
            "Инвентаризация.",
            "Контракт.",
            "Модель.",
            "Сверка.",
            "Live-клиент.",
            "Продвижение.",
            "не должен быть единственным местом, где",
            "отсутствует и в корне, и под `Soll/raw`",
        ).forEach { control ->
            assertTrue("Missing semantic-layer knowledge control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: " +
                "bi_knowledge_added_semantic_layer_deferred_no_financial_reports",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-94b02ac6da81-43712e77076a40a8-verification.md",
            "1 BI knowledge note added",
            "6 adoption gates defined",
            "0 current financial reports",
            "0 measured report-automation value",
            "HabrSberBiSemanticLayerSourceTriageTest",
        ).forEach { evidence ->
            assertTrue("Missing BI source verification evidence: $evidence", verification.contains(evidence))
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
