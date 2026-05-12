package com.soll.domain.scanner

data class ScannerSettings(
    val duplicatePolicy: ScannerDuplicatePolicy = ScannerDuplicatePolicy.COUNT_REPEATS,
)

enum class ScannerDuplicatePolicy(val storageKey: String) {
    COUNT_REPEATS("count_repeats"),
    IGNORE_EXISTING("ignore_existing"),
    ;

    companion object {
        fun fromStorage(value: String?): ScannerDuplicatePolicy =
            entries.firstOrNull { it.storageKey == value } ?: COUNT_REPEATS
    }
}
