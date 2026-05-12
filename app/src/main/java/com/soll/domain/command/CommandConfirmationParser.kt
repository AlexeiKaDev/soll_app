package com.soll.domain.command

data class ConfirmedCommandArgs(
    val args: String?,
    val confirmed: Boolean,
)

object CommandConfirmationParser {
    private val confirmSuffix = Regex("""(?:^|\s)--confirm\s*$""")

    fun parse(args: String?): ConfirmedCommandArgs {
        val raw = args?.trim()
        if (raw.isNullOrEmpty()) {
            return ConfirmedCommandArgs(args = raw, confirmed = false)
        }
        val confirmed = confirmSuffix.containsMatchIn(raw)
        val normalized = if (confirmed) {
            raw.replace(confirmSuffix, "").trim().ifEmpty { null }
        } else {
            raw
        }
        return ConfirmedCommandArgs(args = normalized, confirmed = confirmed)
    }
}
