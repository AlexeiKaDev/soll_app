package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgenticDataBenchSollEvalTemplateTest {
    @Test
    fun `AgenticDataBench signal becomes a safe minimal Soll eval template`() {
        val fixtureFile = projectFile(
            "docs/knowledge/soll-source-monitoring-kb-eval-v1.json",
        )
        val fixtureText = fixtureFile.readText()
        val fixture = JSONObject(fixtureText)
        val knowledge = projectFile(
            "docs/knowledge/soll-source-monitoring-kb-eval-template.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-3b2b9e10dc85f521-verification.md",
        ).readText()

        assertEquals("soll-source-kb-eval-v1", fixture.getString("suite_id"))
        assertEquals(1, fixture.getInt("version"))
        assertEquals(
            "source-item/9011e13c06d6/3b2b9e10dc85f521",
            fixture.getString("source_ref"),
        )

        val dataPolicy = fixture.getJSONObject("data_policy")
        assertEquals("synthetic", dataPolicy.getString("fixture_class"))
        assertFalse(dataPolicy.getBoolean("contains_personal_data"))
        assertFalse(dataPolicy.getBoolean("contains_credentials"))
        assertEquals("disabled", dataPolicy.getString("network_access"))
        assertEquals("forbidden", dataPolicy.getString("external_side_effects"))
        assertEquals("ephemeral_output_only", dataPolicy.getString("writes"))

        val expectedSkills = setOf(
            "source.ingest",
            "source.normalize",
            "source.deduplicate",
            "source.policy",
            "source.rank",
            "source.summarize",
            "source.change_detection",
            "source.task_proposal",
            "kb.retrieve",
            "kb.cite",
            "kb.conflict_resolution",
            "kb.abstain",
            "output.structured",
            "safety.no_side_effects",
        )
        val declaredSkills = fixture.getJSONArray("skill_taxonomy").toStringSet()
        assertEquals(expectedSkills, declaredSkills)

        val expectedTaskIds = (1..8).map { index ->
            "EVAL-SMKB-${index.toString().padStart(2, '0')}"
        }.toSet()
        val tasks = fixture.getJSONArray("tasks")
        val observedTaskIds = mutableSetOf<String>()
        val observedFamilies = mutableSetOf<String>()
        val observedSkills = mutableSetOf<String>()

        assertEquals(8, tasks.length())
        repeat(tasks.length()) { index ->
            val task = tasks.getJSONObject(index)
            observedTaskIds += task.getString("id")
            observedFamilies += task.getString("agent_family")
            observedSkills += task.getJSONArray("skill_tags").toStringSet()

            assertTrue(task.getString("title").isNotBlank())
            assertTrue(task.getString("prompt").isNotBlank())
            assertTrue(task.getJSONObject("input").length() > 0)
            assertTrue(task.getJSONObject("expected_output").length() > 0)

            val metrics = task.getJSONArray("metrics")
            assertTrue("Every case needs a metric", metrics.length() > 0)
            repeat(metrics.length()) { metricIndex ->
                val metric = metrics.getJSONObject(metricIndex)
                assertTrue(metric.getString("name").isNotBlank())
                assertTrue(metric.getString("type").isNotBlank())
                assertTrue(metric.has("target"))
            }

            val safety = task.getJSONArray("safety_assertions")
            assertTrue("Every case needs a safety guard", safety.length() > 0)
            assertTrue(task.getJSONArray("skill_tags").toStringSet().contains("safety.no_side_effects"))
        }

        assertEquals(expectedTaskIds, observedTaskIds)
        assertEquals(setOf("source_monitoring", "knowledge_base"), observedFamilies)
        assertEquals(declaredSkills, observedSkills)

        val suiteMetricNames = fixture.getJSONArray("suite_metrics").let { metrics ->
            buildSet {
                repeat(metrics.length()) { index -> add(metrics.getJSONObject(index).getString("name")) }
            }
        }
        assertEquals(
            setOf(
                "schema_valid_rate",
                "task_success_rate",
                "macro_metric_score",
                "skill_coverage",
                "citation_precision",
                "citation_recall",
                "hallucinated_reference_count",
                "unsafe_side_effect_count",
            ),
            suiteMetricNames,
        )

        val promotionGate = fixture.getJSONObject("promotion_gate")
        assertEquals(7, promotionGate.getInt("minimum_passed_tasks"))
        assertEquals(
            setOf("EVAL-SMKB-02", "EVAL-SMKB-07", "EVAL-SMKB-08"),
            promotionGate.getJSONArray("critical_tasks").toStringSet(),
        )
        assertTrue(promotionGate.getBoolean("all_safety_assertions_must_pass"))

        listOf(
            "## Source and testbed audit",
            "`testbed/tasks/dev.jsonl`",
            "`testbed/gold/<task>/`",
            "## Minimal case contract",
            "## Eight safe cases",
            "## Skill taxonomy and coverage",
            "## Scoring and promotion",
            "## Offline run protocol",
            "Actual source-monitoring or KB agent evaluation runs completed here: **0**",
        ).forEach { control ->
            assertTrue("Missing eval-template control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: minimal_eval_template_defined_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-3b2b9e10dc85f521-verification.md",
            "source_value:",
            "8 synthetic eval cases",
            "14 controlled skill tags",
            "8 suite metrics",
            "1/1 focused contract test passed",
            "0 external agent runs",
            "AgenticDataBenchSollEvalTemplateTest",
        ).forEach { evidence ->
            assertTrue("Missing eval-template verification evidence: $evidence", verification.contains(evidence))
        }
    }

    private fun org.json.JSONArray.toStringSet(): Set<String> = buildSet {
        repeat(length()) { index -> add(getString(index)) }
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
