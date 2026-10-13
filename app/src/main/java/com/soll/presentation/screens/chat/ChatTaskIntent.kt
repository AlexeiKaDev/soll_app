package com.soll.presentation.screens.chat

private val createTaskCommand = Regex(
    "^\\s*(?:(?:добавь|добавить|создай|создать)\\s+(?:новую\\s+)?задачу|новая\\s+задача|задача\\s*:)[\\s:—-]*(.*)$",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

internal fun explicitTaskBody(content: String): String? = createTaskCommand.matchEntire(content)?.groupValues?.get(1)?.trim()
