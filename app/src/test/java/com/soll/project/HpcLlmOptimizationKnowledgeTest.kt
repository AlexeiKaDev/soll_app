package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HpcLlmOptimizationKnowledgeTest {
    @Test
    fun `HPC LLM signal becomes a bounded optimization knowledge note`() {
        val knowledge = projectFile(
            "docs/knowledge/hpc-llm-neural-network-optimization.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-18476224ced6466187f4a292cee8fdbf-hpc-llm-optimization-audit.md",
        ).readText()

        listOf(
            "task_id: 18476224ced6466187f4a292cee8fdbf",
            "source_ref: insight/5d5e682a1aa1",
            "monitored/habr-yandex-company/20260703-211111-item-a9338673.md",
            "Файл источника отсутствует в изолированном worktree",
            "Trequest = Tqueue + Ttransfer + Tcompute + Tsync",
            "## Шесть слоёв оптимизации",
            "**Алгоритм и модель.**",
            "**Представление чисел.**",
            "**Graph и kernels.**",
            "**Память и движение данных.**",
            "**Serving и scheduling.**",
            "**Распределение по устройствам.**",
            "`Prefill`",
            "TTFT p50/p95",
            "inter-token latency/TPOT p50/p95",
            "`androidRuntimeDefault: soll-backend-route`",
            "`packageIntoAndroidApp: false`",
            "`SollGateway.askModelChat(...)`",
            "`sherpa-onnx` относится к локальным speech workloads",
            "`No heavy local LLM on Android in early phases`",
            "## Семь ворот измеримого эксперимента",
            "**Workload.**",
            "**Baseline.**",
            "**Профиль.**",
            "**Одна гипотеза.**",
            "**Качество и безопасность.**",
            "**Ресурсы и tail.**",
            "**Promotion и rollback.**",
            "Выполнено **0** HPC/LLM benchmark или inference runs",
        ).forEach { control ->
            assertTrue("Missing HPC/LLM knowledge control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: hpc_llm_optimization_note_added_runtime_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-18476224ced6466187f4a292cee8fdbf-hpc-llm-optimization-audit.md",
            "1 HPC/LLM optimization note added",
            "6 optimization layers documented",
            "4 current Soll seams audited",
            "7 measurable experiment gates defined",
            "1/1 focused contract test passed",
            "0 HPC/LLM benchmark or inference runs",
            "0 production/runtime changes",
        ).forEach { evidence ->
            assertTrue("Missing HPC/LLM verification evidence: $evidence", verification.contains(evidence))
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
