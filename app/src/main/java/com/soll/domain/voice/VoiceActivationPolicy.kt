package com.soll.domain.voice

data class VoiceActivationDecision(
    val accepted: Boolean,
    val commandText: String,
    val reason: String? = null,
    val matchedPhrase: String? = null,
)

class VoiceActivationPolicy(
    private val wakePhrases: List<String> = DEFAULT_WAKE_PHRASES,
) {
    fun prepare(text: String, requireWakePhrase: Boolean): VoiceActivationDecision {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            return VoiceActivationDecision(
                accepted = false,
                commandText = "",
                reason = "Речь не распознана.",
            )
        }

        if (!requireWakePhrase) {
            return VoiceActivationDecision(
                accepted = true,
                commandText = cleanText,
            )
        }

        val originalTokens = cleanText.activationTokens(normalize = false)
        val normalizedTokens = cleanText.activationTokens(normalize = true)
        val matchedPhrase = wakePhrases.firstOrNull { phrase ->
            val phraseTokens = phrase.split(" ")
            normalizedTokens.size >= phraseTokens.size &&
                normalizedTokens.take(phraseTokens.size) == phraseTokens
        }

        if (matchedPhrase == null) {
            return VoiceActivationDecision(
                accepted = false,
                commandText = cleanText,
                reason = "Команда проигнорирована: скажите «Солл» перед командой.",
            )
        }

        val commandText = originalTokens
            .drop(matchedPhrase.split(" ").size)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        return if (commandText == null) {
            VoiceActivationDecision(
                accepted = false,
                commandText = cleanText,
                reason = "После «Солл» не прозвучала команда.",
                matchedPhrase = matchedPhrase,
            )
        } else {
            VoiceActivationDecision(
                accepted = true,
                commandText = commandText,
                matchedPhrase = matchedPhrase,
            )
        }
    }

    private fun String.activationTokens(normalize: Boolean): List<String> =
        replace(Regex("[,.:;!?]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .map { token ->
                if (normalize) token.normalizedToken() else token
            }

    private fun String.normalizedToken(): String =
        lowercase()
            .replace('ё', 'е')

    companion object {
        val DEFAULT_WAKE_PHRASES = listOf("солл", "soll", "ок солл", "okay soll")
    }
}
