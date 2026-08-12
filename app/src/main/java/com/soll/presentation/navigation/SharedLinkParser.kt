package com.soll.presentation.navigation

import com.soll.domain.soll.SOLL_FEED_IMPORT_CLIENT_ID_MAX_LENGTH
import java.net.URI

data class SharedLinkPayload(
    val url: String = "",
    val title: String = "",
    val sharedText: String = "",
    val clientId: String = "",
    val validationError: String? = null,
) {
    val canSubmit: Boolean
        get() = url.isNotBlank() && validationError == null
}

object SharedLinkParser {
    const val MAX_URL_LENGTH = 2_048
    const val MAX_TITLE_LENGTH = 240
    const val MAX_SHARED_TEXT_LENGTH = 16_000
    const val MAX_CLIENT_ID_LENGTH = SOLL_FEED_IMPORT_CLIENT_ID_MAX_LENGTH

    private val urlPattern = Regex("""(?i)\bhttps?://[^\s<>\"']+""")
    private val whitespacePattern = Regex("""\s+""")

    fun parse(
        sharedText: String?,
        explicitTitle: String?,
        clientId: String,
    ): SharedLinkPayload {
        val cleanSharedText = sharedText
            .orEmpty()
            .filterNot { it.isISOControlExceptWhitespace() }
            .trim()
            .take(MAX_SHARED_TEXT_LENGTH)
        val match = urlPattern.find(cleanSharedText)
        val candidate = match?.value?.stripTrailingSharePunctuation().orEmpty()
        val validUrl = candidate
            .takeIf { it.length <= MAX_URL_LENGTH }
            ?.takeIf(::isSafeHttpUrl)
            .orEmpty()
        val title = cleanTitle(explicitTitle)
            .ifBlank { deriveTitle(cleanSharedText, match?.range) }

        val error = when {
            cleanSharedText.isBlank() -> "В отправленном тексте нет ссылки"
            candidate.isBlank() -> "Не найдена ссылка HTTP(S)"
            candidate.length > MAX_URL_LENGTH -> "Ссылка слишком длинная"
            validUrl.isBlank() -> "Ссылка имеет неверный или небезопасный формат"
            else -> null
        }

        return SharedLinkPayload(
            url = validUrl,
            title = title,
            sharedText = cleanSharedText,
            clientId = clientId.trim().take(MAX_CLIENT_ID_LENGTH),
            validationError = error,
        )
    }

    private fun deriveTitle(
        sharedText: String,
        matchRange: IntRange?,
    ): String {
        if (matchRange == null) return ""
        val before = sharedText.substring(0, matchRange.first)
        val afterIndex = (matchRange.last + 1).coerceAtMost(sharedText.length)
        val after = sharedText.substring(afterIndex)
        return cleanTitle(before).ifBlank { cleanTitle(after) }
    }

    private fun cleanTitle(value: String?): String = value
        .orEmpty()
        .filterNot(Char::isISOControl)
        .replace(whitespacePattern, " ")
        .trim(' ', '-', '—', '–', ':', '|', '"', '\'', '«', '»')
        .take(MAX_TITLE_LENGTH)

    private fun isSafeHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase()
        scheme in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            value.none(Char::isISOControl)
    }.getOrDefault(false)

    private fun String.stripTrailingSharePunctuation(): String {
        var value = trimEnd('.', ',', ';', ':', '!', '?', '…', '»', '”', '’')
        while (value.endsWith(')') && value.count { it == ')' } > value.count { it == '(' }) {
            value = value.dropLast(1)
        }
        while (value.endsWith(']') && value.count { it == ']' } > value.count { it == '[' }) {
            value = value.dropLast(1)
        }
        while (value.endsWith('}') && value.count { it == '}' } > value.count { it == '{' }) {
            value = value.dropLast(1)
        }
        return value.trimEnd('.', ',', ';', ':', '!', '?', '…', '»', '”', '’')
    }

    private fun Char.isISOControlExceptWhitespace(): Boolean =
        isISOControl() && this != '\n' && this != '\r' && this != '\t'
}
