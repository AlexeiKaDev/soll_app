package com.soll.domain.tts

import java.io.File
import java.util.Locale

/**
 * Token mapping for Natasha VITS2.
 *
 * The safest source of truth is the pack's own `symbols.py`. If it is absent,
 * default to the model-native Hugging Face symbol table and keep the broader
 * legacy `infer_onnx.py` table only as an explicit fallback profile.
 */
object NatashaSymbolTokenizer {

    data class Profile(
        val symbolToId: Map<Char, Int>,
        val sourceLabel: String,
    )

    private const val modelPunctuation = " !+,-.:;?«»—"
    private const val legacyInferPunctuation = " !()+,-./:;<>?«»́‑–—’“”„…"
    private const val letters = "абвгдежзийклмнопрстуфхцчшщъыьэюяё"
    private const val lettersIpa =
        "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ"

    private val modelDefaultProfile = buildProfile(
        punctuation = modelPunctuation,
        letters = letters,
        lettersIpa = lettersIpa,
        sourceLabel = "huggingface symbols.py",
    )

    private val legacyExpandedProfile = buildProfile(
        punctuation = legacyInferPunctuation,
        letters = letters,
        lettersIpa = lettersIpa,
        sourceLabel = "legacy infer_onnx.py",
    )

    private val latinToCyr: Map<Char, String> = mapOf(
        'a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е",
        'f' to "ф", 'g' to "г", 'h' to "х", 'i' to "и", 'j' to "й",
        'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н", 'o' to "о",
        'p' to "п", 'q' to "к", 'r' to "р", 's' to "с", 't' to "т",
        'u' to "у", 'v' to "в", 'w' to "в", 'x' to "кс", 'y' to "ы", 'z' to "з",
    )

    fun defaultProfile(): Profile = modelDefaultProfile

    fun legacyProfile(): Profile = legacyExpandedProfile

    fun loadProfileFromPack(root: File): Profile? {
        val symbolsFile = File(root, "symbols.py")
        if (!symbolsFile.exists() || !symbolsFile.isFile) return null
        val text = runCatching { symbolsFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val punctuation = parsePythonString(text, "_punctuation") ?: return null
        val letters = parsePythonString(text, "_letters") ?: return null
        val lettersIpa = parsePythonString(text, "_letters_ipa") ?: return null
        return buildProfile(
            punctuation = punctuation,
            letters = letters,
            lettersIpa = lettersIpa,
            sourceLabel = "pack symbols.py",
        )
    }

    /** Subset of upstream [normalize_russian]: lowercase + whitespace cleanup + latin→cyrillic. */
    fun normalizeLight(text: String): String {
        val lower = text
            .replace("\r", " ")
            .replace("\n", " ")
            .replace('\t', ' ')
            .lowercase(Locale("ru", "RU"))
        val sb = StringBuilder(lower.length * 2)
        var previousWasSpace = false
        var i = 0
        while (i < lower.length) {
            val c = lower[i]
            if (c.isWhitespace()) {
                if (!previousWasSpace) {
                    sb.append(' ')
                    previousWasSpace = true
                }
                i++
                continue
            }
            previousWasSpace = false
            if (i + 2 < lower.length && lower.startsWith("sch", i)) {
                sb.append("ск")
                i += 3
                continue
            }
            if (i + 1 < lower.length) {
                when (lower.substring(i, i + 2)) {
                    "sh" -> {
                        sb.append('ш')
                        i += 2
                        continue
                    }
                    "ch" -> {
                        sb.append('ч')
                        i += 2
                        continue
                    }
                    "th" -> {
                        sb.append('з')
                        i += 2
                        continue
                    }
                    "ph" -> {
                        sb.append('ф')
                        i += 2
                        continue
                    }
                    "kh" -> {
                        sb.append('х')
                        i += 2
                        continue
                    }
                    "oo" -> {
                        sb.append('у')
                        i += 2
                        continue
                    }
                    "ee" -> {
                        sb.append('и')
                        i += 2
                        continue
                    }
                }
            }
            val repl = latinToCyr[c]
            if (repl != null) sb.append(repl) else sb.append(c)
            i++
        }
        return sb.toString().trim()
    }

    fun textToIds(normalized: String, profile: Profile = modelDefaultProfile): LongArray {
        val seq = ArrayList<Long>(normalized.length)
        for (ch in normalized) {
            val id = profile.symbolToId[ch] ?: continue
            seq.add(id.toLong())
        }
        return seq.toLongArray()
    }

    private fun parsePythonString(text: String, variable: String): String? {
        val pattern = Regex("""$variable\s*=\s*(['"])(.*?)\1""", setOf(RegexOption.DOT_MATCHES_ALL))
        return pattern.find(text)?.groupValues?.getOrNull(2)
    }

    private fun buildProfile(
        punctuation: String,
        letters: String,
        lettersIpa: String,
        sourceLabel: String,
    ): Profile {
        val symbolToId = buildMap {
            var i = 0
            fun addString(value: String) {
                for (ch in value) put(ch, i++)
            }
            put('_', i++)
            addString(punctuation)
            addString(letters)
            addString(lettersIpa)
        }
        return Profile(symbolToId = symbolToId, sourceLabel = sourceLabel)
    }
}
