package com.soll.domain.securitylab

import org.json.JSONArray
import org.json.JSONObject

object SensitivePayloadRedactor {
    private const val REDACTED = "[REDACTED]"

    private val keyValueSecret = Regex(
        pattern = """(\b[\w.-]*(?:token|password|secret)[\w.-]*\s*[=:]\s*)([^&;,\s]+)""",
        options = setOf(RegexOption.IGNORE_CASE),
    )

    fun redactSecrets(payload: String?): String? {
        val value = payload?.trim() ?: return null
        if (value.isBlank()) return value
        return redactJson(value) ?: keyValueSecret.replace(value) { match ->
            match.groupValues[1] + REDACTED
        }
    }

    private fun redactJson(value: String): String? {
        val root = runCatching {
            when {
                value.startsWith("{") -> JSONObject(value)
                value.startsWith("[") -> JSONArray(value)
                else -> null
            }
        }.getOrNull() ?: return null
        return redactJsonValue(root).toString()
    }

    private fun redactJsonValue(value: Any?): Any? =
        when (value) {
            is JSONObject -> redactObject(value)
            is JSONArray -> redactArray(value)
            else -> value
        }

    private fun redactObject(source: JSONObject): JSONObject {
        val target = JSONObject()
        source.keys().forEach { key ->
            target.put(
                key,
                if (key.isSensitiveKey()) REDACTED else redactJsonValue(source.opt(key)),
            )
        }
        return target
    }

    private fun redactArray(source: JSONArray): JSONArray {
        val target = JSONArray()
        for (index in 0 until source.length()) {
            target.put(redactJsonValue(source.opt(index)))
        }
        return target
    }

    private fun String.isSensitiveKey(): Boolean {
        val key = lowercase()
        return "token" in key || "password" in key || "secret" in key
    }
}
