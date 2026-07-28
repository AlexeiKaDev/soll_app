package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9935HexagonVisionRopeKnowledgeTest {
    @Test
    fun `b9935 stays local to Qualcomm Hexagon vision processing without collection features`() {
        val note = projectFile(
            "docs/knowledge/llama-cpp-b9935-qualcomm-hexagon-vision-rope.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-cc8bb3edd290c856-verification.md",
        ).readText().normalizeWhitespace()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText()

        listOf(
            "raw/monitored/llama-cpp-releases/20260709-233427-b9935-02f5a101.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9935",
            "https://github.com/ggml-org/llama.cpp/pull/25216",
            "commit `f2d1c2f` после пяти commits",
            "`ggml/src/ggml-hexagon/ggml-hexagon.cpp`",
            "`ggml/src/ggml-hexagon/htp/rope-ops.c`",
            "VISION RoPE",
            "Qwen2-VL/Qwen3-VL vision encoder",
            "strided и non-contiguous fixtures",
            "DMA row payload отделён от DDR row stride",
            "исправленным SPAD pitch",
            "только для локального on-device inference и vision preprocessing",
            "Qualcomm Hexagon",
            "не разрешает сетевое сканирование",
            "не разрешает скрытый сбор данных",
            "camera capture, telemetry, upload",
            "`0` network scans, `0` uploads, `0` hidden collection",
            "Изменено `0` production/runtime файлов",
            "выполнено `0` on-device inference runs",
        ).forEach { control ->
            assertTrue("Missing b9935 Hexagon VISION RoPE control: $control", note.contains(control))
        }

        listOf(
            "task_id: 61be2475b60a4f888c1c3fdd0f409133",
            "source_ref: source-item/d0cd9479f2a2/cc8bb3edd290c856",
            "source_processing_result: qualcomm_hexagon_vision_rope_local_only_boundary_documented",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-cc8bb3edd290c856-verification.md",
            "1 short Soll_app KB note added",
            "2 official upstream surfaces, 5 commits and 2 changed Hexagon files audited",
            "4 privacy/safety prohibitions recorded",
            "0 production/runtime changes and 0 on-device inference runs",
            "LlamaCppB9935HexagonVisionRopeKnowledgeTest",
            "`1/1` focused test passed",
        ).forEach { evidence ->
            assertTrue("Missing b9935 verification evidence: $evidence", audit.contains(evidence))
        }

        assertTrue(activeDefaults.contains("\"tag\": \"b10068\""))
        assertTrue(activeDefaults.contains("\"androidRuntimeDefault\": \"soll-backend-route\""))
        assertTrue(activeDefaults.contains("\"packageIntoAndroidApp\": false"))
        assertEquals(2, Regex("\"backend\"\\s*:\\s*\"cpu\"").findAll(activeDefaults).count())
        assertFalse(activeDefaults.contains("\"backend\": \"hexagon\""))
        assertFalse(activeDefaults.contains("\"tag\": \"b9935\""))
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

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
