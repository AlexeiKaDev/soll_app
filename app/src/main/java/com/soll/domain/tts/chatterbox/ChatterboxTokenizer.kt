package com.soll.domain.tts.chatterbox

import org.json.JSONObject
import java.io.File

object ChatterboxTokenizer {

    data class Profile(
        val vocab: Map<String, Int>,
        val mergeRanks: Map<Pair<String, String>, Int>,
        val bracketTokens: Set<String>,
        val unkId: Int,
        val bosId: Int,
        val eosId: Int,
        val exaggerationTokenId: Int,
        val startSpeechTokenId: Int,
        val stopSpeechTokenId: Int,
        val maxTextTokenId: Int,
        val sourceLabel: String,
    ) {
        fun languageToken(languageId: String): String = "[${languageId.lowercase()}]"
    }

    fun loadFromPack(root: File): Profile? {
        val tokenizerFile = File(root, "tokenizer.json")
        if (!tokenizerFile.isFile) return null
        return runCatching {
            val json = JSONObject(tokenizerFile.readText(Charsets.UTF_8))
            val vocabObject = json.getJSONObject("model").getJSONObject("vocab")
            val vocab = LinkedHashMap<String, Int>(vocabObject.length())
            val bracketTokens = LinkedHashSet<String>()
            val vocabKeys = vocabObject.keys()
            while (vocabKeys.hasNext()) {
                val key = vocabKeys.next()
                val id = vocabObject.getInt(key)
                vocab[key] = id
                if (key.startsWith("[") && key.endsWith("]")) {
                    bracketTokens += key
                }
            }

            val mergesArray = json.getJSONObject("model").getJSONArray("merges")
            val mergeRanks = LinkedHashMap<Pair<String, String>, Int>(mergesArray.length())
            for (index in 0 until mergesArray.length()) {
                val merge = mergesArray.getString(index)
                val parts = merge.split(' ', limit = 2)
                if (parts.size == 2) {
                    mergeRanks[parts[0] to parts[1]] = index
                }
            }

            val postProcessor = json.optJSONObject("post_processor")
            val specialTokens = postProcessor?.optJSONObject("special_tokens")
            fun specialId(name: String, fallbackToken: String, defaultId: Int): Int {
                val ids = specialTokens
                    ?.optJSONObject(name)
                    ?.optJSONArray("ids")
                    ?.takeIf { it.length() > 0 }
                return when {
                    ids != null -> ids.optInt(0, defaultId)
                    vocab.containsKey(fallbackToken) -> vocab.getValue(fallbackToken)
                    else -> defaultId
                }
            }

            Profile(
                vocab = vocab,
                mergeRanks = mergeRanks,
                bracketTokens = bracketTokens,
                unkId = vocab["[UNK]"] ?: 1,
                bosId = specialId("BOS", "[START]", 255),
                eosId = specialId("EOS", "[STOP]", 0),
                exaggerationTokenId = specialId("EXAGGERATION", "<EXAGGERATION>", 6563),
                startSpeechTokenId = specialId("START_SPEECH", "<START_SPEECH>", 6561),
                stopSpeechTokenId = vocab["[STOP_SPEECH]"] ?: 6562,
                maxTextTokenId = MAX_TEXT_TOKEN_ID,
                sourceLabel = "tokenizer.json",
            )
        }.getOrNull()
    }

    fun encodeText(
        text: String,
        profile: Profile,
        languageId: String = "ru",
    ): LongArray {
        val normalized = normalizeText(text, languageId)
        if (normalized.isBlank()) return longArrayOf()
        val symbols = splitPreservingBracketTokens(normalized, profile.bracketTokens)
        val merged = applyBpe(symbols, profile.mergeRanks)
        val payloadIds = merged.map { token ->
            normalizeTextTokenId(profile.vocab[token] ?: profile.unkId, profile)
        }
        val finalIds = ArrayList<Long>(payloadIds.size + 5)
        finalIds += profile.exaggerationTokenId.toLong()
        finalIds += profile.bosId.toLong()
        finalIds.addAll(payloadIds.map(Int::toLong))
        finalIds += profile.eosId.toLong()
        finalIds += profile.startSpeechTokenId.toLong()
        finalIds += profile.startSpeechTokenId.toLong()
        return finalIds.toLongArray()
    }

    private fun normalizeText(text: String, languageId: String): String {
        val collapsed = text
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace('\n', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (collapsed.isBlank()) return ""
        val safeText = if (languageId.equals("ru", ignoreCase = true)) {
            collapsed.canonicalizeRussianForOnnx()
        } else {
            collapsed
        }
        return buildString(collapsed.length + languageId.length + 10) {
            append('[')
            append(languageId.lowercase())
            append(']')
            safeText.forEach { ch ->
                if (ch == ' ') append("[SPACE]") else append(ch)
            }
        }
    }

    private fun String.canonicalizeRussianForOnnx(): String =
        buildString(length) {
            this@canonicalizeRussianForOnnx.forEach { ch ->
                append(
                    when (ch) {
                        'й' -> 'и'
                        'Й' -> 'И'
                        'ё' -> 'е'
                        'Ё' -> 'Е'
                        else -> ch
                    },
                )
            }
        }

    private fun normalizeTextTokenId(id: Int, profile: Profile): Int {
        return when {
            id in 0..profile.maxTextTokenId -> id
            id >= profile.startSpeechTokenId -> id
            else -> profile.unkId
        }
    }

    private fun splitPreservingBracketTokens(
        input: String,
        bracketTokens: Set<String>,
    ): List<String> {
        val out = ArrayList<String>(input.length)
        var index = 0
        while (index < input.length) {
            if (input[index] == '[') {
                val close = input.indexOf(']', startIndex = index + 1)
                if (close > index) {
                    val token = input.substring(index, close + 1)
                    if (token in bracketTokens) {
                        out += token
                        index = close + 1
                        continue
                    }
                }
            }
            val codePoint = input.codePointAt(index)
            out += String(Character.toChars(codePoint))
            index += Character.charCount(codePoint)
        }
        return out
    }

    private fun applyBpe(
        symbols: List<String>,
        mergeRanks: Map<Pair<String, String>, Int>,
    ): List<String> {
        if (symbols.size <= 1) return symbols
        var current = symbols.toMutableList()
        while (current.size > 1) {
            var bestIndex = -1
            var bestRank = Int.MAX_VALUE
            for (i in 0 until current.lastIndex) {
                val rank = mergeRanks[current[i] to current[i + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestIndex = i
                }
            }
            if (bestIndex < 0) break
            val merged = ArrayList<String>(current.size - 1)
            var index = 0
            while (index < current.size) {
                if (index == bestIndex) {
                    merged += current[index] + current[index + 1]
                    index += 2
                } else {
                    merged += current[index]
                    index++
                }
            }
            current = merged
        }
        return current
    }

    private const val MAX_TEXT_TOKEN_ID = 2351
}
