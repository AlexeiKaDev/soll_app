package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualMemorySourceTriageTest {
    @Test
    fun `dual memory pattern integrates recent findings with cited durable knowledge`() {
        val fixture = JSONObject(
            projectFile(
                "docs/knowledge/dual-memory-source-triage-read-only-v1.json",
            ).readText(),
        )
        val note = projectFile(
            "docs/knowledge/dual-memory-source-triage.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-9c2c0ff8f35e233d-verification.md",
        ).readText()

        assertEquals(1, fixture.getInt("schema_version"))
        assertEquals(
            "dual-memory-source-triage-read-only-v1",
            fixture.getString("prototype_id"),
        )
        assertEquals("08940ee570904d13b27286a5dffbc2a7", fixture.getString("task_id"))
        assertEquals(
            "source-item/9011e13c06d6/9c2c0ff8f35e233d",
            fixture.getString("source_ref"),
        )
        assertEquals("untrusted_external_content", fixture.getString("source_trust"))
        assertEquals("static_synthetic_read_only_contract", fixture.getString("mode"))
        assertFalse(fixture.getJSONObject("source").getBoolean("raw_present_in_worktree"))

        val expectedLayers = setOf("recent_findings", "durable_kb")
        val evidenceByRecord = mutableMapOf<String, String>()
        val layerByRecord = mutableMapOf<String, String>()
        var recentFindingCount = 0
        var durableRecordCount = 0

        val layers = fixture.getJSONArray("memory_layers")
        assertEquals(2, layers.length())
        repeat(layers.length()) { layerIndex ->
            val layer = layers.getJSONObject(layerIndex)
            val layerId = layer.getString("id")
            val records = layer.getJSONArray("records")
            assertTrue(expectedLayers.contains(layerId))

            repeat(records.length()) { recordIndex ->
                val record = records.getJSONObject(recordIndex)
                val recordId = record.getString("id")
                assertFalse(
                    "Duplicate dual-memory record: $recordId",
                    evidenceByRecord.containsKey(recordId),
                )
                evidenceByRecord[recordId] = record.getString("evidence_ref")
                layerByRecord[recordId] = layerId
                assertTrue(record.getString("claim").isNotBlank())
                assertTrue(record.getString("source_ref").isNotBlank())

                when (layerId) {
                    "recent_findings" -> {
                        recentFindingCount += 1
                        assertTrue(record.getString("observed_at").isNotBlank())
                        assertTrue(record.getString("expires_at").isNotBlank())
                        assertTrue(
                            record.getString("evidence_ref")
                                .startsWith("fixture://recent-findings/"),
                        )
                    }
                    "durable_kb" -> {
                        durableRecordCount += 1
                        assertEquals(
                            "repository_contract",
                            record.getString("review_status"),
                        )
                        val evidencePath = record.getString("evidence_ref").substringBefore("#")
                        assertTrue(
                            "Durable KB citation does not exist: $evidencePath",
                            projectFile(evidencePath).isFile,
                        )
                    }
                    else -> error("Unknown memory layer: $layerId")
                }
            }
        }
        assertEquals(expectedLayers, layerByRecord.values.toSet())
        assertEquals(3, recentFindingCount)
        assertEquals(2, durableRecordCount)

        val contract = fixture.getJSONObject("retrieval_contract")
        assertEquals(
            listOf("recent_findings", "durable_kb"),
            contract.getJSONArray("search_order").toStringList(),
        )
        assertEquals("read_only_advisory", contract.getString("authority"))
        assertTrue(contract.getBoolean("citation_required_per_claim"))
        assertFalse(contract.getBoolean("automatic_task_creation"))
        assertFalse(contract.getBoolean("automatic_source_priority_change"))
        assertFalse(contract.getBoolean("automatic_kb_promotion"))
        assertEquals(0, contract.getInt("memory_writes_executed"))
        assertEquals(0, contract.getInt("network_calls"))
        assertEquals(0, contract.getInt("external_tool_calls"))
        assertEquals(0, contract.getInt("robotic_control_actions"))

        var casesSearchingBothLayers = 0
        var casesCitingBothLayers = 0
        var responsesWithCitations = 0
        var validatedCitationLinks = 0
        var freshnessConflicts = 0
        var readOnlyResponses = 0

        val cases = fixture.getJSONArray("cases")
        assertEquals(3, cases.length())
        repeat(cases.length()) { caseIndex ->
            val case = cases.getJSONObject(caseIndex)
            assertEquals(expectedLayers, case.getJSONArray("searched_layers").toStringSet())
            casesSearchingBothLayers += 1

            val selectedRecords = case.getJSONArray("selected_record_ids").toStringSet()
            assertTrue(selectedRecords.isNotEmpty())
            assertTrue(evidenceByRecord.keys.containsAll(selectedRecords))

            val response = case.getJSONObject("response")
            assertEquals("read_only_advisory", response.getString("authority"))
            readOnlyResponses += 1
            assertEquals(0, response.getJSONArray("action_proposals").length())
            assertEquals("none", response.getString("control_action"))

            val citations = response.getJSONArray("citations")
            assertTrue("Every response needs citations", citations.length() > 0)
            responsesWithCitations += 1
            val citedLayers = mutableSetOf<String>()
            val citedRecords = mutableSetOf<String>()
            repeat(citations.length()) { citationIndex ->
                val citation = citations.getJSONObject(citationIndex)
                val recordId = citation.getString("record_id")
                assertTrue(selectedRecords.contains(recordId))
                assertEquals(layerByRecord.getValue(recordId), citation.getString("memory_layer"))
                assertEquals(
                    evidenceByRecord.getValue(recordId),
                    citation.getString("evidence_ref"),
                )
                citedLayers += citation.getString("memory_layer")
                citedRecords += recordId
                validatedCitationLinks += 1
            }
            assertEquals(selectedRecords, citedRecords)
            assertEquals(expectedLayers, citedLayers)
            casesCitingBothLayers += 1

            if (response.getString("status") == "grounded_with_freshness_conflict") {
                freshnessConflicts += 1
                assertEquals(setOf("REC-001", "REC-002", "KB-001"), selectedRecords)
            }
        }

        val metrics = fixture.getJSONObject("metrics")
        assertEquals(recentFindingCount, metrics.getInt("recent_findings"))
        assertEquals(durableRecordCount, metrics.getInt("durable_kb_records"))
        assertEquals(cases.length(), metrics.getInt("cases"))
        assertEquals(
            casesSearchingBothLayers,
            metrics.getInt("cases_searching_both_layers"),
        )
        assertEquals(casesCitingBothLayers, metrics.getInt("cases_citing_both_layers"))
        assertEquals(responsesWithCitations, metrics.getInt("responses_with_citations"))
        assertEquals(validatedCitationLinks, metrics.getInt("validated_citation_links"))
        assertEquals(freshnessConflicts, metrics.getInt("freshness_conflicts_preserved"))
        assertEquals(readOnlyResponses, metrics.getInt("read_only_responses"))
        assertEquals(0, metrics.getInt("uncited_claims"))
        assertEquals(0, metrics.getInt("memory_writes_executed"))
        assertEquals(0, metrics.getInt("network_calls"))
        assertEquals(0, metrics.getInt("external_tool_calls"))
        assertEquals(0, metrics.getInt("robotic_control_actions"))

        listOf(
            "## Safe mapping",
            "**recent findings**",
            "**durable KB**",
            "retrieval searches both layers",
            "cites every selected record",
            "Memory supplies evidence, never authority",
            "## Read-only prototype",
            "no robotic perception, planning, actuation or action execution",
        ).forEach { control ->
            assertTrue("Missing dual-memory triage control: $control", note.contains(control))
        }

        listOf(
            "task_id: 08940ee570904d13b27286a5dffbc2a7",
            "source_processing_result: " +
                "dual_memory_source_triage_read_only_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-9c2c0ff8f35e233d-verification.md",
            "source_value:",
            "3 recent findings",
            "2 durable KB records",
            "3/3 synthetic queries",
            "7/7 citation links",
            "1/1 focused contract test passed",
            "0 robotic control actions",
            "DualMemorySourceTriageTest",
        ).forEach { evidence ->
            assertTrue("Missing dual-memory verification evidence: $evidence", verification.contains(evidence))
        }
    }

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map(::getString)

    private fun org.json.JSONArray.toStringSet(): Set<String> = toStringList().toSet()

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
