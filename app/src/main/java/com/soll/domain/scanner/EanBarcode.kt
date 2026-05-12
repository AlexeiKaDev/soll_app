package com.soll.domain.scanner

object EanBarcode {
    fun normalize(raw: String): String =
        raw.filter { it.isDigit() }

    fun detectFormat(raw: String): String {
        val normalized = normalize(raw)
        return when {
            isValidEan13(normalized) -> BarcodeFormat.EAN_13.name
            isValidEan8(normalized) -> BarcodeFormat.EAN_8.name
            normalized.isNotBlank() -> BarcodeFormat.NUMERIC.name
            else -> BarcodeFormat.TEXT.name
        }
    }

    fun isValidEan13(value: String): Boolean =
        value.length == 13 && value.all(Char::isDigit) && checksum(value.dropLast(1)) == value.last().digitToInt()

    fun isValidEan8(value: String): Boolean =
        value.length == 8 && value.all(Char::isDigit) && checksum(value.dropLast(1)) == value.last().digitToInt()

    fun checksum(body: String): Int {
        require(body.all(Char::isDigit)) { "EAN должен содержать только цифры" }
        val sum = body.reversed().mapIndexed { index, char ->
            val digit = char.digitToInt()
            if (index % 2 == 0) digit * 3 else digit
        }.sum()
        return (10 - (sum % 10)) % 10
    }
}

enum class BarcodeFormat {
    EAN_13,
    EAN_8,
    QR_CODE,
    AZTEC,
    DATA_MATRIX,
    PDF_417,
    CODE_128,
    CODE_93,
    CODE_39,
    CODABAR,
    UPC_A,
    UPC_E,
    ITF,
    NUMERIC,
    TEXT,
}

data class ScanConfirmationResult(
    val confirmed: Boolean,
    val ignoredByCooldown: Boolean,
    val value: String,
    val format: String,
    val matchCount: Int,
    val requiredMatches: Int,
)

class ScanConfirmationGate(
    private val requiredMatches: Int = 2,
    private val windowMs: Long = 1800L,
    private val cooldownMs: Long = 2500L,
) {
    private var pendingKey: String? = null
    private var pendingValue: String = ""
    private var pendingFormat: String = ""
    private var pendingCount: Int = 0
    private var pendingStartedAt: Long = 0L
    private var lastConfirmedKey: String? = null
    private var lastConfirmedAt: Long = 0L

    fun observe(
        rawValue: String,
        format: String,
        nowMs: Long = System.currentTimeMillis(),
    ): ScanConfirmationResult {
        val value = rawValue.trim()
        if (value.isBlank()) {
            return ScanConfirmationResult(
                confirmed = false,
                ignoredByCooldown = false,
                value = "",
                format = format,
                matchCount = 0,
                requiredMatches = requiredMatches,
            )
        }

        val key = "${format}:${value}"
        if (key == lastConfirmedKey && nowMs - lastConfirmedAt < cooldownMs) {
            return ScanConfirmationResult(
                confirmed = false,
                ignoredByCooldown = true,
                value = value,
                format = format,
                matchCount = requiredMatches,
                requiredMatches = requiredMatches,
            )
        }

        val samePending = key == pendingKey && nowMs - pendingStartedAt <= windowMs
        if (!samePending) {
            pendingKey = key
            pendingValue = value
            pendingFormat = format
            pendingCount = 1
            pendingStartedAt = nowMs
            return ScanConfirmationResult(
                confirmed = requiredMatches <= 1,
                ignoredByCooldown = false,
                value = value,
                format = format,
                matchCount = 1,
                requiredMatches = requiredMatches,
            )
        }

        pendingCount += 1
        val confirmed = pendingCount >= requiredMatches
        if (confirmed) {
            lastConfirmedKey = key
            lastConfirmedAt = nowMs
            pendingKey = null
        }

        return ScanConfirmationResult(
            confirmed = confirmed,
            ignoredByCooldown = false,
            value = pendingValue,
            format = pendingFormat,
            matchCount = pendingCount.coerceAtMost(requiredMatches),
            requiredMatches = requiredMatches,
        )
    }

    fun reset() {
        pendingKey = null
        pendingValue = ""
        pendingFormat = ""
        pendingCount = 0
        pendingStartedAt = 0L
        lastConfirmedKey = null
        lastConfirmedAt = 0L
    }
}
