package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLabCutMlHeuristicsKnowledgeTest {
    @Test
    fun `DeepLabCut signal becomes a grounded video analysis knowledge card`() {
        val knowledge = projectFile(
            "docs/knowledge/deeplabcut-ml-heuristics-video-analysis.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-d7a5045b5d8c41b2a72f36ff07c387b2-deeplabcut-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: d7a5045b5d8c41b2a72f36ff07c387b2",
            "source_ref: insight/dc672114c375",
            "scope: CV-автоматизация для видеоанализа",
            "Компьютерное зрение против рутины: как мы ускорили анализ поведения лабораторных мышей",
            "monitored/habr-yandex-company/20260703-211111-item-2f69c75f.md",
            "Файл не включён в этот изолированный worktree",
            "DeepLabCut закрывает слой markerless pose estimation",
            "Это ещё не распознанное поведение",
            "## Что переносим в базу знаний",
            "**Pose — промежуточное доказательство.**",
            "**Эвристики должны быть явными.**",
            "**Неуверенность маршрутизируется человеку.**",
            "**Разметка улучшается итерациями.**",
            "**Ценность — сэкономленное время при сохранённом качестве.**",
            "per-frame (x, y, likelihood)",
            "Train, validation и holdout делятся по животному или сессии",
            "enter threshold",
            "exit threshold",
            "hysteresis",
            "event_type, start_frame, end_frame, confidence",
            "event precision, recall, F1, temporal IoU",
            "minutes_saved_per_video_hour",
            "## Семь ворот безопасного пилота Soll",
            "**Leakage-safe holdout.**",
            "**Human-review gate.**",
            "**Value gate.**",
            "не добавляет DeepLabCut, Python runtime, веса модели",
            "Измеренная экономия времени пока `0`",
        ).forEach { control ->
            assertTrue("Missing DeepLabCut knowledge control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: d7a5045b5d8c41b2a72f36ff07c387b2",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/dc672114c375",
            "source_processing_result: deeplabcut_ml_heuristics_knowledge_card_added",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-d7a5045b5d8c41b2a72f36ff07c387b2-deeplabcut-audit.md",
            "1 knowledge card added",
            "5 transferable insights documented",
            "4 CV workflow stages documented",
            "2 metric layers separated",
            "7 measurable pilot gates defined",
            "1/1 focused contract test passed",
            "0 labeling, training or video-analysis runs",
            "0 Android/runtime changes",
            "DeepLabCutMlHeuristicsKnowledgeTest",
            "Measured time saving remains `0`",
        ).forEach { evidence ->
            assertTrue("Missing DeepLabCut audit evidence: $evidence", verification.contains(evidence))
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

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
