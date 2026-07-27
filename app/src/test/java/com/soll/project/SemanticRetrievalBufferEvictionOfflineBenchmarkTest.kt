package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticRetrievalBufferEvictionOfflineBenchmarkTest {
    @Test
    fun `source becomes a safe research note and anonymized offline benchmark`() {
        val fixture = JSONObject(
            projectFile(
                "docs/knowledge/semantic-retrieval-buffer-eviction-offline-v1.json",
            ).readText(),
        )
        val note = projectFile(
            "docs/knowledge/semantic-retrieval-buffer-eviction-offline-benchmark.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-f0bb9e2e01664c42-verification.md",
        ).readText().normalizeWhitespace()
        val memoryDao = projectFile(
            "app/src/main/java/com/soll/data/local/dao/AssistantMemoryDao.kt",
        ).readText()
        val memoryRepository = projectFile(
            "app/src/main/java/com/soll/data/repository/AssistantMemoryRepository.kt",
        ).readText()

        assertEquals(1, fixture.getInt("schema_version"))
        assertEquals(
            "semantic-retrieval-buffer-eviction-offline-v1",
            fixture.getString("benchmark_id"),
        )
        assertEquals("520850f55a864bf58f347cf8211d04fe", fixture.getString("task_id"))
        assertEquals(
            "source-item/9011e13c06d6/f0bb9e2e01664c42",
            fixture.getString("source_ref"),
        )
        assertEquals("untrusted_external_content", fixture.getString("source_trust"))
        assertEquals("offline_synthetic_anonymized_replay", fixture.getString("mode"))

        val dataPolicy = fixture.getJSONObject("data_policy")
        assertEquals(
            "synthetic_anonymized_local_tasks",
            dataPolicy.getString("fixture_class"),
        )
        assertFalse(dataPolicy.getBoolean("contains_personal_data"))
        assertFalse(dataPolicy.getBoolean("contains_credentials"))
        assertFalse(dataPolicy.getBoolean("contains_user_text"))
        listOf(
            "runtime_memory_reads",
            "network_access",
            "external_integrations",
            "automatic_actions",
        ).forEach { key -> assertEquals("forbidden", dataPolicy.getString(key)) }

        val cacheAudit = fixture.getJSONObject("cache_audit")
        assertTrue(cacheAudit.getBoolean("assistant_memory_present"))
        assertFalse(cacheAudit.getBoolean("semantic_retrieval_cache_present"))
        assertTrue(cacheAudit.getBoolean("bounded_recent_view_present"))
        assertFalse(cacheAudit.getBoolean("automatic_eviction_present"))
        assertTrue(
            memoryDao.contains(
                "ORDER BY pinned DESC, updated_at DESC LIMIT :limit",
            ),
        )
        assertFalse(memoryDao.contains("last_used_at"))
        assertTrue(
            memoryRepository.contains(
                "if (!settingsRepository.assistantMemoryEnabled) return",
            ),
        )
        assertFalse(memoryRepository.contains("evict", ignoreCase = true))

        val policies = fixture.getJSONArray("policy_contracts")
        assertEquals(3, policies.length())
        assertEquals(
            setOf("fifo", "lru", "score_based"),
            (0 until policies.length())
                .map { index -> policies.getJSONObject(index).getString("id") }
                .toSet(),
        )
        val scorePolicy = (0 until policies.length())
            .map(policies::getJSONObject)
            .single { it.getString("id") == "score_based" }
        assertFalse(scorePolicy.getBoolean("score_updates_during_replay"))
        assertTrue(scorePolicy.getBoolean("incoming_candidate_can_be_rejected"))

        val scenarios = fixture.getJSONArray("scenarios")
        assertEquals(3, scenarios.length())
        repeat(scenarios.length()) { scenarioIndex ->
            val scenario = scenarios.getJSONObject(scenarioIndex)
            assertEquals(3, scenario.getInt("capacity"))
            val tasks = scenario.getJSONArray("tasks")
            val taskIds = mutableSetOf<String>()
            repeat(tasks.length()) { taskIndex ->
                val task = tasks.getJSONObject(taskIndex)
                assertEquals(setOf("id", "relevance_score"), task.keys().asSequence().toSet())
                val taskId = task.getString("id")
                assertTrue("Task id is not anonymized: $taskId", taskId.matches(Regex("T\\d{2}")))
                assertTrue("Duplicate task id: $taskId", taskIds.add(taskId))
                assertTrue(task.getDouble("relevance_score") in 0.0..1.0)
            }
            val requests = scenario.getJSONArray("requests")
            repeat(requests.length()) { requestIndex ->
                assertTrue(taskIds.contains(requests.getString(requestIndex)))
            }
        }

        val safety = fixture.getJSONObject("safety")
        listOf(
            "network_calls",
            "external_tool_calls",
            "runtime_memory_reads",
            "user_data_records",
            "memory_writes",
            "automatic_actions",
            "production_changes",
        ).forEach { key -> assertEquals("Unsafe count for $key", 0, safety.getInt(key)) }

        listOf(
            "task_id: 520850f55a864bf58f347cf8211d04fe",
            "source_ref: source-item/9011e13c06d6/f0bb9e2e01664c42",
            "https://huggingface.co/papers/2607.00394",
            "https://arxiv.org/abs/2607.00394",
            "arXiv:2607.00394v1",
            "absent from this isolated worktree",
            "`AssistantMemory`",
            "но не production semantic retrieval cache",
            "`lastUsedAt`",
            "does not support a blanket claim that FIFO is always better",
            "runtime or user-memory reads: `0`",
            "user-data records: `0`",
            "memory writes or automatic actions: `0`",
        ).forEach { control ->
            assertTrue("Missing research-note control: $control", note.contains(control))
        }

        listOf(
            "task_id: 520850f55a864bf58f347cf8211d04fe",
            "project: soll_app",
            "source_ref: source-item/9011e13c06d6/f0bb9e2e01664c42",
            "source_processing_result: research_note_and_offline_eviction_benchmark_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-f0bb9e2e01664c42-verification.md",
            "source_value:",
            "3 policies replayed on 3 synthetic anonymized traces/30 requests",
            "FIFO 5 hits/16.67%, LRU 5 hits/16.67%, score-based 12 hits/40.00%",
            "score-based gained 7 hits and reduced evictions 16 to 9",
            "2/2 focused tests passed",
            "0 runtime/user-memory reads",
            "SemanticRetrievalBufferEvictionOfflineBenchmarkTest",
        ).forEach { evidence ->
            assertTrue(
                "Missing benchmark verification evidence: $evidence",
                verification.contains(evidence),
            )
        }
    }

    @Test
    fun `FIFO LRU and score eviction replay matches every durable metric`() {
        val fixture = JSONObject(
            projectFile(
                "docs/knowledge/semantic-retrieval-buffer-eviction-offline-v1.json",
            ).readText(),
        )
        val policies = listOf(Policy.FIFO, Policy.LRU, Policy.SCORE_BASED)
        val aggregate = policies.associateWith { Metrics() }.toMutableMap()
        val scenarios = fixture.getJSONArray("scenarios")

        repeat(scenarios.length()) { scenarioIndex ->
            val scenario = scenarios.getJSONObject(scenarioIndex)
            val scores = scenario.getJSONArray("tasks").let { tasks ->
                (0 until tasks.length()).associate { taskIndex ->
                    val task = tasks.getJSONObject(taskIndex)
                    task.getString("id") to task.getDouble("relevance_score")
                }
            }
            val requests = scenario.getJSONArray("requests").let { values ->
                (0 until values.length()).map(values::getString)
            }

            policies.forEach { policy ->
                val actual = replay(
                    capacity = scenario.getInt("capacity"),
                    scores = scores,
                    requests = requests,
                    policy = policy,
                )
                assertMetrics(
                    expected = scenario.getJSONObject("expected")
                        .getJSONObject(policy.fixtureId),
                    actual = actual,
                    label = "${scenario.getString("id")}/${policy.fixtureId}",
                )
                aggregate[policy] = requireNotNull(aggregate[policy]) + actual
            }
        }

        val aggregateExpected = fixture.getJSONObject("aggregate_expected")
        policies.forEach { policy ->
            assertMetrics(
                expected = aggregateExpected.getJSONObject(policy.fixtureId),
                actual = requireNotNull(aggregate[policy]),
                label = "aggregate/${policy.fixtureId}",
            )
        }

        val fifo = requireNotNull(aggregate[Policy.FIFO])
        val lru = requireNotNull(aggregate[Policy.LRU])
        val score = requireNotNull(aggregate[Policy.SCORE_BASED])
        assertEquals(5, fifo.hits)
        assertEquals(5, lru.hits)
        assertEquals(12, score.hits)
        assertEquals(7, score.hits - fifo.hits)
        assertTrue(score.evictions < fifo.evictions)

        val recencyTrap = scenarios.getJSONObject(0).getJSONObject("expected")
        assertTrue(
            recencyTrap.getJSONObject("fifo").getInt("hits") >
                recencyTrap.getJSONObject("lru").getInt("hits"),
        )
        val recencyFriendly = scenarios.getJSONObject(1).getJSONObject("expected")
        assertTrue(
            recencyFriendly.getJSONObject("lru").getInt("hits") >
                recencyFriendly.getJSONObject("fifo").getInt("hits"),
        )
    }

    private fun replay(
        capacity: Int,
        scores: Map<String, Double>,
        requests: List<String>,
        policy: Policy,
    ): Metrics {
        require(capacity > 0)
        val entries = mutableMapOf<String, CacheEntry>()
        var metrics = Metrics()

        requests.forEachIndexed { index, taskId ->
            val sequence = index + 1
            val score = requireNotNull(scores[taskId])
            val existing = entries[taskId]
            metrics = metrics.copy(
                requests = metrics.requests + 1,
                availableRelevanceMass = metrics.availableRelevanceMass + score,
            )

            if (existing != null) {
                existing.lastAccessSequence = sequence
                metrics = metrics.copy(
                    hits = metrics.hits + 1,
                    hitRelevanceMass = metrics.hitRelevanceMass + score,
                )
                return@forEachIndexed
            }

            metrics = metrics.copy(misses = metrics.misses + 1)
            entries[taskId] = CacheEntry(
                taskId = taskId,
                score = score,
                insertionSequence = sequence,
                lastAccessSequence = sequence,
            )
            if (entries.size <= capacity) return@forEachIndexed

            val victim = entries.values.minWithOrNull(policy.evictionComparator)
                ?: error("No eviction candidate")
            entries.remove(victim.taskId)
            metrics = metrics.copy(
                evictions = metrics.evictions + 1,
                admissionRejections = metrics.admissionRejections +
                    if (victim.taskId == taskId) 1 else 0,
            )
        }

        return metrics
    }

    private fun assertMetrics(expected: JSONObject, actual: Metrics, label: String) {
        assertEquals("$label requests", expected.getInt("requests"), actual.requests)
        assertEquals("$label hits", expected.getInt("hits"), actual.hits)
        assertEquals("$label misses", expected.getInt("misses"), actual.misses)
        assertEquals("$label evictions", expected.getInt("evictions"), actual.evictions)
        assertEquals(
            "$label admission rejections",
            expected.getInt("admission_rejections"),
            actual.admissionRejections,
        )
        assertEquals("$label hit rate", expected.getDouble("hit_rate"), actual.hitRate, 1e-12)
        assertEquals(
            "$label hit relevance mass",
            expected.getDouble("hit_relevance_mass"),
            actual.hitRelevanceMass,
            1e-12,
        )
        assertEquals(
            "$label available relevance mass",
            expected.getDouble("available_relevance_mass"),
            actual.availableRelevanceMass,
            1e-12,
        )
        assertEquals(
            "$label relevance hit rate",
            expected.getDouble("relevance_hit_rate"),
            actual.relevanceHitRate,
            1e-12,
        )
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

    private enum class Policy(
        val fixtureId: String,
        val evictionComparator: Comparator<CacheEntry>,
    ) {
        FIFO(
            fixtureId = "fifo",
            evictionComparator = compareBy<CacheEntry> { it.insertionSequence }
                .thenBy { it.taskId },
        ),
        LRU(
            fixtureId = "lru",
            evictionComparator = compareBy<CacheEntry> { it.lastAccessSequence }
                .thenBy { it.insertionSequence }
                .thenBy { it.taskId },
        ),
        SCORE_BASED(
            fixtureId = "score_based",
            evictionComparator = compareBy<CacheEntry> { it.score }
                .thenBy { it.insertionSequence }
                .thenBy { it.taskId },
        ),
    }

    private data class CacheEntry(
        val taskId: String,
        val score: Double,
        val insertionSequence: Int,
        var lastAccessSequence: Int,
    )

    private data class Metrics(
        val requests: Int = 0,
        val hits: Int = 0,
        val misses: Int = 0,
        val evictions: Int = 0,
        val admissionRejections: Int = 0,
        val hitRelevanceMass: Double = 0.0,
        val availableRelevanceMass: Double = 0.0,
    ) {
        val hitRate: Double
            get() = if (requests == 0) 0.0 else hits.toDouble() / requests

        val relevanceHitRate: Double
            get() = if (availableRelevanceMass == 0.0) {
                0.0
            } else {
                hitRelevanceMass / availableRelevanceMass
            }

        operator fun plus(other: Metrics): Metrics = Metrics(
            requests = requests + other.requests,
            hits = hits + other.hits,
            misses = misses + other.misses,
            evictions = evictions + other.evictions,
            admissionRejections = admissionRejections + other.admissionRejections,
            hitRelevanceMass = hitRelevanceMass + other.hitRelevanceMass,
            availableRelevanceMass = availableRelevanceMass + other.availableRelevanceMass,
        )
    }
}
