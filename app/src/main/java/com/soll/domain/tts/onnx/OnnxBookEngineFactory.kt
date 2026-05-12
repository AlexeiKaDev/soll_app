package com.soll.domain.tts.onnx

import android.os.Build
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class OnnxDeviceProfile(
    val manufacturer: String,
    val model: String,
    val cpuCores: Int,
    val availableMemoryMb: Int?,
)

@Singleton
class OnnxBookEngineFactory @Inject constructor() {

    fun detectDeviceProfile(): OnnxDeviceProfile {
        return OnnxDeviceProfile(
            manufacturer = Build.MANUFACTURER.lowercase(Locale.US),
            model = Build.MODEL.uppercase(Locale.US),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            availableMemoryMb = null, // Can be filled from ActivityManager when wiring runtime chooser.
        )
    }

    fun pickDefaultModel(profile: OnnxDeviceProfile): OnnxTtsModel {
        val isLikelyS200 = profile.manufacturer.contains("doogee") && profile.model.contains("S200")
        if (isLikelyS200) {
            return pickRussianOptimizedModel(profile) ?: OnnxTtsModelCatalog.recommendedForS200().first()
        }

        // Conservative fallback for unknown Android devices.
        return OnnxTtsModelCatalog.byId("kokoro_82m")
            ?: OnnxTtsModelCatalog.models.first()
    }

    /**
     * RU-first selection for constrained Android devices.
     * Note: Chatterbox now lives in a dedicated runtime and is not part of ONNX External execution.
     */
    fun pickRussianOptimizedModel(profile: OnnxDeviceProfile, allowHeavy: Boolean = false): OnnxTtsModel? {
        val limitMb = if (allowHeavy) 2000 else 900
        val candidates = OnnxTtsModelCatalog.recommendedRussianByQualitySize(limitMb)
        if (candidates.isEmpty()) return null
        val isLikelyS200 = profile.manufacturer.contains("doogee") && profile.model.contains("S200")
        if (isLikelyS200) {
            return candidates.firstOrNull { it.id == "moss_nano_100m" } ?: candidates.first()
        }
        return candidates.first()
    }
}
