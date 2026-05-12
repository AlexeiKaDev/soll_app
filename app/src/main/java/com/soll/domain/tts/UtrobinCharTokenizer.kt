package com.soll.domain.tts

import org.json.JSONObject
import java.io.File

/**
 * Mirrors sherpa-onnx [offline-tts-character-frontend] for add_blank=1, use_eos_bos=1,
 * and default ids 0/0/0 (from model metadata). Produces a flat id sequence for HF VITS.
 */
internal object UtrobinCharTokenizer {

    fun loadTokenMap(tokensFile: File): Map<Char, Int> {
        return when (tokensFile.extension.lowercase()) {
            "json" -> loadJsonTokenMap(tokensFile)
            else -> loadTextTokenMap(tokensFile)
        }
    }

    private fun loadTextTokenMap(tokensFile: File): Map<Char, Int> {
        val map = LinkedHashMap<Char, Int>()
        tokensFile.readLines().forEach { lineRaw ->
            val line = lineRaw.trimEnd('\r').trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.size == 1) {
                val id = parts[0].toIntOrNull() ?: return@forEach
                map[' '] = id
            } else {
                val id = parts.last().toIntOrNull() ?: return@forEach
                val sym = parts.dropLast(1).joinToString(" ")
                if (sym == "<PAD>" || sym == "<EOS>" || sym == "<BOS>" || sym == "<BLNK>") return@forEach
                if (sym.length != 1) return@forEach
                map[sym[0]] = id
            }
        }
        if (' ' !in map) {
            map['_']?.let { blankLike -> map[' '] = blankLike }
        }
        return map
    }

    private fun loadJsonTokenMap(file: File): Map<Char, Int> {
        val text = file.readText(Charsets.UTF_8)
        val root = JSONObject(text)
        val vocab = root.optJSONObject("model")?.optJSONObject("vocab") ?: root
        val map = LinkedHashMap<Char, Int>()
        val keys = vocab.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            val id = vocab.optInt(token, Int.MIN_VALUE)
            if (id == Int.MIN_VALUE) continue
            when (token) {
                "<pad>", "<PAD>", "<s>", "</s>", "<unk>", "<BLNK>", "<BOS>", "<EOS>" -> Unit
                " " -> map[' '] = id
                "▁" -> map.putIfAbsent(' ', id)
                else -> if (token.length == 1) {
                    map[token[0]] = id
                }
            }
        }
        if (' ' !in map) {
            map['_']?.let { blankLike -> map[' '] = blankLike }
        }
        return map
    }

    /**
     * Same control flow as [ConvertTextToTokenIds] (add_blank branch), then flattens all sentence chunks.
     * [defaults] from sherpa when ONNX keys are missing: add_blank=1, use_eos_bos=1, blank/bos/eos=0.
     */
    fun textToFlatIds(
        textLowercasedUtf8: String,
        token2id: Map<Char, Int>,
        useEosBos: Boolean = true,
        blankId: Int = 0,
        bosId: Int = 0,
        eosId: Int = 0,
    ): LongArray {
        val ans = mutableListOf<MutableList<Long>>()
        var thisSentence = mutableListOf<Long>()
        if (useEosBos) thisSentence.add(bosId.toLong())
        thisSentence.add(blankId.toLong())

        for (c in textLowercasedUtf8) {
            val id = token2id[c]
            if (id != null) {
                thisSentence.add(id.toLong())
                thisSentence.add(blankId.toLong())
            }
            if (c == '.' || c == ':' || c == '?' || c == '!') {
                if (useEosBos) thisSentence.add(eosId.toLong())
                ans.add(thisSentence)
                thisSentence = mutableListOf()
                if (useEosBos) thisSentence.add(bosId.toLong())
                thisSentence.add(blankId.toLong())
            }
        }
        if (useEosBos) thisSentence.add(eosId.toLong())
        if (thisSentence.size > 1 + (if (useEosBos) 1 else 0)) {
            ans.add(thisSentence)
        }
        var total = 0
        for (a in ans) total += a.size
        if (total == 0) return longArrayOf()
        val out = LongArray(total)
        var i = 0
        for (a in ans) {
            for (v in a) out[i++] = v
        }
        return out
    }
}
