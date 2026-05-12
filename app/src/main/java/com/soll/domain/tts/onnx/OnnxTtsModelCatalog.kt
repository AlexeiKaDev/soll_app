package com.soll.domain.tts.onnx

enum class OnnxModelTier {
    LIGHT,
    MEDIUM,
    HEAVY,
}

data class OnnxTtsModel(
    val id: String,
    val displayName: String,
    val huggingFaceRepo: String,
    val license: String,
    val languages: List<String>,
    val sizeMbFp32: Int,
    val sizeMbFp16: Int?,
    val sizeMbInt4: Int?,
    val estimatedRamMb: Int,
    val tier: OnnxModelTier,
    val russianQualityScore: Int, // 0..10 subjective rank for RU speech
    val hasRussian: Boolean,
)

/**
 * Curated ONNX shortlist for Android devices like Doogee S200.
 * Models are metadata-only for now; runtime sessions are created by factory implementations.
 */
object OnnxTtsModelCatalog {
    /** Есть готовый исполнитель в приложении (ONNX External). */
    fun hasAndroidOnnxRuntime(id: String): Boolean = id == "kokoro_82m" || id == "chatterbox_multilingual"

    val models: List<OnnxTtsModel> = listOf(
        OnnxTtsModel(
            id = "kokoro_82m",
            displayName = "Kokoro 82M",
            huggingFaceRepo = "onnx-community/Kokoro-82M-v1.0-ONNX",
            license = "Apache-2.0",
            languages = listOf("en"),
            sizeMbFp32 = 326,
            sizeMbFp16 = 163,
            sizeMbInt4 = 92,
            estimatedRamMb = 512,
            tier = OnnxModelTier.LIGHT,
            russianQualityScore = 0,
            hasRussian = false,
        ),
        OnnxTtsModel(
            id = "moss_nano_100m",
            displayName = "MOSS TTS Nano 100M",
            huggingFaceRepo = "OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX",
            license = "Apache-2.0",
            languages = listOf("ru", "en", "multilingual"),
            sizeMbFp32 = 673,
            sizeMbFp16 = null,
            sizeMbInt4 = null,
            estimatedRamMb = 700,
            tier = OnnxModelTier.MEDIUM,
            russianQualityScore = 6,
            hasRussian = true,
        ),
        OnnxTtsModel(
            id = "supertonic_tts_2",
            displayName = "Supertonic TTS 2",
            huggingFaceRepo = "onnx-community/Supertonic-TTS-2-ONNX",
            license = "OpenRAIL",
            languages = listOf("en", "ko", "es", "pt", "fr"),
            sizeMbFp32 = 263,
            sizeMbFp16 = null,
            sizeMbInt4 = null,
            estimatedRamMb = 300,
            tier = OnnxModelTier.LIGHT,
            russianQualityScore = 0,
            hasRussian = false,
        ),
        OnnxTtsModel(
            id = "chatterbox_multilingual",
            displayName = "Chatterbox Multilingual",
            huggingFaceRepo = "onnx-community/chatterbox-multilingual-ONNX",
            license = "MIT",
            languages = listOf("23+ languages", "ru", "en"),
            sizeMbFp32 = 4980,
            sizeMbFp16 = 1040,
            sizeMbInt4 = 350,
            estimatedRamMb = 6000,
            tier = OnnxModelTier.HEAVY,
            russianQualityScore = 9,
            hasRussian = true,
        ),
        OnnxTtsModel(
            id = "chatterbox_turbo",
            displayName = "Chatterbox Turbo",
            huggingFaceRepo = "ResembleAI/chatterbox-turbo-ONNX",
            license = "MIT",
            languages = listOf("en"),
            sizeMbFp32 = 7390,
            sizeMbFp16 = 1660,
            sizeMbInt4 = 720,
            estimatedRamMb = 8000,
            tier = OnnxModelTier.HEAVY,
            russianQualityScore = 0,
            hasRussian = false,
        ),
    )

    fun recommendedForS200(): List<OnnxTtsModel> {
        return listOfNotNull(byId("kokoro_82m"))
    }

    fun byId(id: String): OnnxTtsModel? = models.firstOrNull { it.id == id }

    /**
     * Returns Russian-capable models sorted by best quality/size compromise.
     * Lower memory devices should use low [maxModelSizeMb] (e.g. 900 for INT4-friendly packs).
     */
    fun recommendedRussianByQualitySize(maxModelSizeMb: Int = 900): List<OnnxTtsModel> {
        return models
            .asSequence()
            .filter { it.hasRussian }
            .filter { hasAndroidOnnxRuntime(it.id) }
            .filter { (it.sizeMbInt4 ?: it.sizeMbFp16 ?: it.sizeMbFp32) <= maxModelSizeMb }
            .sortedWith(
                compareByDescending<OnnxTtsModel> { it.russianQualityScore }
                    .thenBy { it.sizeMbInt4 ?: it.sizeMbFp16 ?: it.sizeMbFp32 }
            )
            .toList()
    }
}
