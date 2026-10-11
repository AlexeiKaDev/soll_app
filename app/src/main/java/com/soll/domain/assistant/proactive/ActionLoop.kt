package com.soll.domain.assistant.proactive

/** Tracks proposals without executing them; explicit acceptance consumes one proposal. */
class ActionLoop {
    private val pending = linkedMapOf<String, ProactiveSuggestion>()
    private val accepted = mutableSetOf<String>()

    @Synchronized
    fun submit(suggestions: List<ProactiveSuggestion>) {
        pending.clear()
        suggestions.filterNot { it.id in accepted }.forEach { pending[it.id] = it }
    }

    @Synchronized
    fun accept(id: String): ProactiveSuggestion? {
        val suggestion = pending.remove(id) ?: return null
        if (!accepted.add(id)) return null
        return suggestion
    }
}
