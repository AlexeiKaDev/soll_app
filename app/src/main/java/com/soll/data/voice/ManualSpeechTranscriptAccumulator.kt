package com.soll.data.voice

internal class ManualSpeechTranscriptAccumulator {
    private val segments = mutableListOf<String>()
    private var pendingPartial: String = ""

    fun reset() {
        segments.clear()
        pendingPartial = ""
    }

    fun updatePartial(text: String?) {
        val partial = text.normalizedSpeechText()
        if (partial.isNotBlank()) {
            pendingPartial = partial
        }
    }

    fun commitResult(text: String?) {
        val finalText = text.normalizedSpeechText()
        append(finalText.ifBlank { pendingPartial })
        pendingPartial = ""
    }

    fun commitPendingPartial() {
        append(pendingPartial)
        pendingPartial = ""
    }

    fun text(): String? =
        segments.joinToString(" ").trim().takeIf { it.isNotBlank() }

    private fun append(text: String) {
        if (text.isNotBlank() && segments.lastOrNull() != text) {
            segments += text
        }
    }
}

private fun String?.normalizedSpeechText(): String =
    this?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
