package com.soll.domain.assistant.proactive

import org.junit.Assert.*
import org.junit.Test

class ActionLoopTest {
    private val suggestion = ProactiveSuggestion(
        "id", "Title", "Detail", ProactiveSuggestionPriority.HIGH, 1f,
        ProactiveSuggestionAction.OPEN_APP_SETTINGS,
    )

    @Test fun proposalsRequireAcceptanceAndAreConsumedOnce() {
        val loop = ActionLoop()
        var executions = 0
        loop.submit(listOf(suggestion))
        assertEquals(0, executions)
        assertNull(loop.accept("unknown"))
        if (loop.accept("id") != null) executions++
        if (loop.accept("id") != null) executions++
        loop.submit(listOf(suggestion))
        if (loop.accept("id") != null) executions++
        assertEquals(1, executions)
    }

    @Test fun withdrawnSuggestionCannotExecute() {
        val loop = ActionLoop()
        loop.submit(listOf(suggestion))
        loop.submit(emptyList())
        assertNull(loop.accept("id"))
    }
}
