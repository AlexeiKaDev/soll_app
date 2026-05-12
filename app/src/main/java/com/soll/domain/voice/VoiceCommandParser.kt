package com.soll.domain.voice

sealed class VoiceCommand {
    data object Health : VoiceCommand()
    data object Sync : VoiceCommand()
    data object TaskSummary : VoiceCommand()
    data object MusicPlay : VoiceCommand()
    data object MusicPause : VoiceCommand()
    data object MusicNext : VoiceCommand()
    data object MusicPrevious : VoiceCommand()
    data class RawNote(val text: String) : VoiceCommand()
    data class Unknown(val text: String) : VoiceCommand()
}

class VoiceCommandParser {
    fun parse(text: String): VoiceCommand {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return VoiceCommand.Unknown(text)

        rawNotePrefix(normalized)?.let { prefix ->
            val noteText = text.drop(prefix.length).trim()
            if (noteText.isNotBlank()) return VoiceCommand.RawNote(noteText)
        }

        return when {
            normalized.contains("музык") &&
                (normalized.contains("след") || normalized.contains("дальше") || normalized.contains("следующий")) ->
                VoiceCommand.MusicNext

            normalized.contains("музык") &&
                (normalized.contains("пред") || normalized.contains("назад") || normalized.contains("прошлый")) ->
                VoiceCommand.MusicPrevious

            normalized.contains("музык") &&
                (normalized.contains("пауз") || normalized.contains("останов")) ->
                VoiceCommand.MusicPause

            normalized.contains("включи музыку") ||
                normalized.contains("запусти музыку") ||
                normalized == "музыка" ||
                normalized.contains("играй музыку") -> VoiceCommand.MusicPlay

            normalized.contains("синх") ||
                normalized.contains("обнови") ||
                normalized.contains("обновить") -> VoiceCommand.Sync

            normalized.contains("задач") ||
                normalized.contains("дела") ||
                normalized.contains("план") -> VoiceCommand.TaskSummary

            normalized.contains("статус") ||
                normalized.contains("состояние") ||
                normalized.contains("сервер") -> VoiceCommand.Health

            else -> VoiceCommand.Unknown(text)
        }
    }

    private fun rawNotePrefix(normalized: String): String? =
        listOf(
            "создай заметку",
            "запиши заметку",
            "заметка",
            "запиши",
        ).firstOrNull { normalized.startsWith(it) }
}
