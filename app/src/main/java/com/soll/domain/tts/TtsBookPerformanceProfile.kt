package com.soll.domain.tts

/**
 * Preset for book TTS: threads, chunk merging, and Sherpa/Piper parallelism.
 * See [docs/tts-s200-model-shortlist.md] for rationale on Doogee S200–class devices.
 */
enum class TtsBookPerformanceProfile(val storageKey: String) {
    BATTERY("battery"),
    BALANCED("balanced"),
    QUALITY("quality"),
    ;

    companion object {
        fun fromStorage(value: String?): TtsBookPerformanceProfile =
            entries.find { it.storageKey == value } ?: BALANCED

        /** ONNX intra-op / comparable thread counts (1–4). */
        fun ortIntraThreads(profile: TtsBookPerformanceProfile): Int = when (profile) {
            BATTERY -> 1
            BALANCED -> 2
            QUALITY -> 4
        }

        /** Sherpa OfflineTts numThreads (1–4). */
        fun sherpaNumThreads(profile: TtsBookPerformanceProfile, processors: Int): Int {
            val p = processors.coerceAtLeast(1)
            return when (profile) {
                BATTERY -> 1
                BALANCED -> p.coerceIn(2, 3)
                QUALITY -> p.coerceIn(2, 4)
            }
        }

        /** Merge short neighbour sentences: max single-sentence length / combined cap. */
        fun chunkMergeLimits(profile: TtsBookPerformanceProfile): Pair<Int, Int> = when (profile) {
            BATTERY -> 300 to 520
            BALANCED -> 220 to 360
            QUALITY -> 160 to 280
        }
    }
}
