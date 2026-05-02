package com.soll.domain.tts

import java.util.Locale

/**
 * Symbol table from [shigabeev/vits2-inference infer_onnx.py](https://github.com/shigabeev/vits2-inference)
 * — must match [frappuccino/vits2_ru_natasha](https://huggingface.co/frappuccino/vits2_ru_natasha) ONNX.
 */
object NatashaSymbolTokenizer {

    private val punctuation: String = buildString {
        append(" !()+,-./:;<>?«»")
        append('\u0301')
        append('\u2011')
        append('\u2013')
        append('\u2014')
        append('\u2019')
        append('\u2019')
        append('\u201c')
        append('\u201d')
        append('\u201e')
        append('\u2026')
    }

    private const val letters = "абвгдежзийклмнопрстуфхцчшщъыьэюяё"

    private val lettersIpa: String = buildString {
        append(
            "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑ",
        )
        append('\u02BC')
        append("ʴʰʱʲʷˠˤ˞↓↑→↗↘")
        append('\u0027')
        append('\u0329')
        append('\u0027')
        append('\u1D7B')
    }

    private val symbolToId: Map<Char, Int> = buildMap {
        var i = 0
        fun addString(s: String) {
            for (ch in s) put(ch, i++)
        }
        put('_', i++)
        addString(punctuation)
        addString(letters)
        addString(lettersIpa)
    }

    private val latinToCyr: Map<Char, String> = mapOf(
        'a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е",
        'f' to "ф", 'g' to "г", 'h' to "х", 'i' to "и", 'j' to "й",
        'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н", 'o' to "о",
        'p' to "п", 'q' to "к", 'r' to "р", 's' to "с", 't' to "т",
        'u' to "у", 'v' to "в", 'w' to "в", 'x' to "кс", 'y' to "ы", 'z' to "з",
    )

    /** Subset of upstream [normalize_russian]: lowercase + latin→cyrillic; enough for book text. */
    fun normalizeLight(text: String): String {
        val lower = text.lowercase(Locale("ru", "RU"))
        val sb = StringBuilder(lower.length * 2)
        var i = 0
        while (i < lower.length) {
            val c = lower[i]
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
        return sb.toString()
    }

    fun textToIds(normalized: String): LongArray {
        val seq = ArrayList<Long>(normalized.length)
        for (ch in normalized) {
            val id = symbolToId[ch] ?: continue
            seq.add(id.toLong())
        }
        return seq.toLongArray()
    }
}
