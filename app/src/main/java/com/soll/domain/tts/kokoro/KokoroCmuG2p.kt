package com.soll.domain.tts.kokoro

import android.content.Context
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Упрощённая англ. озвучка для Kokoro: CMUdict (строчный файл) → ARPAbet → IPA-символы из vocab Kokoro.
 * Полное качество как у Misaki/en-кода Kokoro не гарантируется; словарь можно расширять.
 */
internal object KokoroCmuG2p {

    private val CYRILLIC = Regex("\\p{IsCyrillic}")

    private val vowelBases = setOf(
        "AA", "AE", "AH", "AO", "AW", "AY",
        "EH", "ER", "EY", "IH", "IY",
        "OW", "OY", "UH", "UW",
    )

    /** CMU base phoneme → IPA фрагменты (Unicode символы, допускается несколько). */
    private val baseToIpaParts: Map<String, List<String>> = mapOf(
        "AA" to listOf("ɑ"),
        "AE" to listOf("æ"),
        "AH" to listOf("ə"),
        "AO" to listOf("ɔ"),
        "AW" to listOf("a", "ʊ"),
        "AY" to listOf("a", "ɪ"),
        "B" to listOf("b"),
        "CH" to listOf("t", "ʃ"),
        "D" to listOf("d"),
        "DH" to listOf("ð"),
        "EH" to listOf("ɛ"),
        "ER" to listOf("ɝ"),
        "EY" to listOf("e", "ɪ"),
        "F" to listOf("f"),
        "G" to listOf("ɡ"),
        "HH" to listOf("h"),
        "IH" to listOf("ɪ"),
        "IY" to listOf("i"),
        "JH" to listOf("d", "ʒ"),
        "K" to listOf("k"),
        "L" to listOf("l"),
        "M" to listOf("m"),
        "N" to listOf("n"),
        "NG" to listOf("ŋ"),
        "OW" to listOf("o", "ʊ"),
        "OY" to listOf("ɔ", "ɪ"),
        "P" to listOf("p"),
        "R" to listOf("ɹ"),
        "S" to listOf("s"),
        "SH" to listOf("ʃ"),
        "T" to listOf("t"),
        "TH" to listOf("θ"),
        "UH" to listOf("ʊ"),
        "UW" to listOf("u"),
        "V" to listOf("v"),
        "W" to listOf("w"),
        "Y" to listOf("j"),
        "Z" to listOf("z"),
        "ZH" to listOf("ʒ"),
    )

    /** Простая «выговор по буквам» для OOV (англ.). */
    private val spellLettersArpa: Map<Char, List<String>> = mapOf(
        'A' to listOf("EY"),
        'B' to listOf("B", "IY"),
        'C' to listOf("S", "IY"),
        'D' to listOf("D", "IY"),
        'E' to listOf("IY"),
        'F' to listOf("EH", "F"),
        'G' to listOf("JH", "IY"),
        'H' to listOf("EY", "CH"),
        'I' to listOf("AY"),
        'J' to listOf("JH", "EY"),
        'K' to listOf("K", "EY"),
        'L' to listOf("EH", "L"),
        'M' to listOf("EH", "M"),
        'N' to listOf("EH", "N"),
        'O' to listOf("OW"),
        'P' to listOf("P", "IY"),
        'Q' to listOf("K", "Y", "UW"),
        'R' to listOf("AA", "R"),
        'S' to listOf("EH", "S"),
        'T' to listOf("T", "IY"),
        'U' to listOf("Y", "UW"),
        'V' to listOf("V", "IY"),
        'W' to listOf("D", "AH", "B", "AH", "L", "Y", "UW"),
        'X' to listOf("EH", "K", "S"),
        'Y' to listOf("W", "AY"),
        'Z' to listOf("Z", "IY"),
    )

    private const val PRIMARY = '\u02C8'
    private const val SECONDARY = '\u02CC'

    fun hasCyrillic(text: String): Boolean = CYRILLIC.containsMatchIn(text)

    fun loadMiniCmudict(context: Context): Map<String, List<String>> {
        val map = LinkedHashMap<String, List<String>>()
        context.assets.open("kokoro/kokoro_en_cmudict_mini.txt").use { raw ->
            BufferedReader(InputStreamReader(raw, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(";")) return@forEach
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size < 2) return@forEach
                    val word = parts.first().trimEnd('.', '!', '?', ',', ';', ':', '"', '\'').uppercase()
                    val phones = parts.drop(1).filter { it.isNotBlank() }
                    if (word.isNotBlank() && phones.isNotEmpty()) {
                        map[word] = phones
                    }
                }
            }
        }
        Timber.i("Kokoro CMU mini lexicon loaded: ${map.size} entries")
        return map
    }

    /**
     * Текст → строка IPA-символов для vocab Kokoro (посимвольный lookup в config vocab).
     * Возвращает null если есть кириллица или после нормализации пусто.
     */
    fun englishTextToPhonemeLine(text: String, lexicon: Map<String, List<String>>): String? {
        if (hasCyrillic(text)) return null
        val tokens = tokenizeWordsKeepingInternals(text)
        if (tokens.isEmpty()) return null
        val sb = StringBuilder()
        var firstWord = true
        for (rawWord in tokens) {
            val core = stripLeadingTrailingQuotes(rawWord).trimEnd('.', '!', '?', ',', ':', ';')
            if (core.isEmpty()) continue
            if (!core.matches(Regex("^[a-zA-Z']+$"))) continue
            val lookupKey = core.replace("'", "").uppercase()
            if (lookupKey.isEmpty()) continue
            val phones = lexicon[lookupKey] ?: spellWordOOV(lookupKey)
            val ipaPieces = arpaPhonesToIpa(phones)
            if (ipaPieces.isEmpty()) continue
            if (!firstWord) sb.append(' ')
            ipaPieces.forEach { part ->
                part.forEach { ch ->
                    sb.append(ch)
                }
            }
            firstWord = false
        }
        val result = sb.toString().trim()
        return result.takeIf { it.isNotBlank() }
    }

    private fun tokenizeWordsKeepingInternals(text: String): List<String> {
        val spaced = text.replace('\u2019', '\'')
        return spaced.split(Regex("\\s+")).mapNotNull { chunk ->
            val trimmed = chunk.trim().trim('"', '\'', '(', ')', '[', ']', '«', '»')
                .trimEnd('.', ',', '!', '?', ':', ';')
            trimmed.takeIf { it.isNotBlank() }
        }
    }

    private fun stripLeadingTrailingQuotes(s: String): String =
        s.trim().trim('"', '\'', '«', '»')

    private fun spellWordOOV(word: String): List<String> {
        val out = mutableListOf<String>()
        for (ch in word) {
            val u = ch.uppercaseChar()
            if (!u.isLetter()) continue
            val seq = spellLettersArpa[u] ?: continue
            out.addAll(seq)
        }
        return out
    }

    private fun arpaPhonesToIpa(phones: List<String>): List<String> {
        val parts = mutableListOf<String>()
        for (p in phones) {
            val (base, stress) = splitArpa(p)
            val ipaChunks = baseToIpaParts[base] ?: run {
                Timber.w("Kokoro CMU: unknown ARPA base $base")
                return emptyList()
            }
            val combined = ipaChunks.joinToString("") { it }
            val stressed = applyStress(combined, base, stress)
            parts.add(stressed)
        }
        return parts
    }

    private fun splitArpa(token: String): Pair<String, Int?> {
        val t = token.uppercase().trim()
        val m = Regex("^([A-Z]{1,3})([012])?$").matchEntire(t)
            ?: run {
                val lettersOnly = t.takeWhile { it.isLetter() && it <= 'Z' }
                return lettersOnly.takeIf { it.length <= 3 }?.let { it to null }
                    ?: (lettersOnly.ifBlank { t } to null)
            }
        val stress = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
        return m.groupValues[1] to stress
    }

    private fun applyStress(ipaWordPart: String, base: String, stress: Int?): String {
        if (stress == null || stress == 0 || base !in vowelBases) return ipaWordPart
        val marker = if (stress == 1) PRIMARY else SECONDARY
        return marker.toString() + ipaWordPart
    }
}
