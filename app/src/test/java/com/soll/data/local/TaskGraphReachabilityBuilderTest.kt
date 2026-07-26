package com.soll.data.local

import com.soll.data.local.dao.TaskGraphReachabilityBuilder
import com.soll.domain.soll.SollTaskGraphEdge
import com.soll.domain.soll.SollTaskGraphNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskGraphReachabilityBuilderTest {
    @Test
    fun `alternative paths share one closure row with a reference count`() {
        val rows = TaskGraphReachabilityBuilder.build(
            scope = "open",
            nodes = listOf("A", "B", "C", "D").map { id ->
                SollTaskGraphNode(id = id, kind = "task", label = id)
            },
            edges = listOf(
                edge("A-B", "A", "B"),
                edge("A-C", "A", "C"),
                edge("B-D", "B", "D"),
                edge("C-D", "C", "D"),
                edge("A-D", "A", "D"),
            ),
        )

        assertEquals(9, rows.size)
        assertEquals(1L, rows.single { it.ancestorId == "A" && it.descendantId == "A" }.pathCount)
        assertEquals(3L, rows.single { it.ancestorId == "A" && it.descendantId == "D" }.pathCount)
        assertFalse(rows.any { it.ancestorId == "D" && it.descendantId == "A" })
    }

    @Test
    fun `cyclic snapshots are rejected before replacing a valid cache`() {
        val error = try {
            TaskGraphReachabilityBuilder.build(
                scope = "open",
                nodes = listOf("A", "B").map { id ->
                    SollTaskGraphNode(id = id, kind = "task", label = id)
                },
                edges = listOf(
                    edge("A-B", "A", "B"),
                    edge("B-A", "B", "A"),
                ),
            )
            fail("Expected a cyclic task graph to be rejected")
            null
        } catch (expected: IllegalArgumentException) {
            expected
        }

        assertTrue(error?.message.orEmpty().contains("acyclic"))
    }

    @Test
    fun `dense snapshots stop at the configured mobile row budget`() {
        val error = try {
            TaskGraphReachabilityBuilder.build(
                scope = "open",
                nodes = listOf("A", "B", "C").map { id ->
                    SollTaskGraphNode(id = id, kind = "task", label = id)
                },
                edges = listOf(
                    edge("A-B", "A", "B"),
                    edge("B-C", "B", "C"),
                ),
                maxRows = 5,
            )
            fail("Expected the configured reachability row budget to reject the snapshot")
            null
        } catch (expected: IllegalArgumentException) {
            expected
        }

        assertTrue(error?.message.orEmpty().contains("exceeds 5 rows"))
    }

    private fun edge(id: String, source: String, target: String): SollTaskGraphEdge =
        SollTaskGraphEdge(
            id = id,
            source = source,
            target = target,
            kind = "contains",
        )
}
