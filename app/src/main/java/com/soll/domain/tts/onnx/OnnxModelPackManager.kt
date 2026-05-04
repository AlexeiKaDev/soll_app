package com.soll.domain.tts.onnx

import android.net.Uri
import com.soll.domain.tts.catalog.DetectedTtsPack
import com.soll.domain.tts.catalog.TtsPackEngineFamily
import com.soll.domain.tts.catalog.TtsPackLibrary
import com.soll.domain.tts.catalog.TtsPackStatus
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledOnnxPack(
    val modelId: String,
    val precision: String,
    val rootDir: String,
    val estimatedSizeMb: Int,
    /** e.g. kokoro_v1 — см. prepare_onnx_pack.py / model_manifest.json */
    val runtimeFamily: String? = null,
    /** Голос Kokoro: файл voices/{id}.bin */
    val kokoroVoice: String? = null,
) {
    fun effectiveRuntimeFamily(): String = runtimeFamily ?: when (modelId) {
        "kokoro_82m" -> "kokoro_v1"
        else -> "unsupported"
    }
}

@Singleton
class OnnxModelPackManager @Inject constructor(
    private val packLibrary: TtsPackLibrary,
) {
    fun importPacksFromTreeUri(treeUri: Uri): Int =
        packLibrary.importFromTreeUri(treeUri)

    fun listInstalledPacks(): List<InstalledOnnxPack> =
        packLibrary.listDetectedPacks()
            .asSequence()
            .filter { it.engineFamily == TtsPackEngineFamily.ONNX_EXTERNAL }
            .filter { it.status == TtsPackStatus.READY }
            .filter { it.isRussianCapable }
            .mapNotNull(::toInstalledOnnxPack)
            .sortedWith(compareBy({ it.modelId }, { it.precision }))
            .toList()

    fun pickBestRussianPack(): InstalledOnnxPack? =
        packLibrary.listDetectedPacks()
            .asSequence()
            .filter { it.engineFamily == TtsPackEngineFamily.ONNX_EXTERNAL }
            .filter { it.status == TtsPackStatus.READY }
            .filter { it.isRussianCapable }
            .mapNotNull(::toInstalledOnnxPack)
            .firstOrNull()

    private fun toInstalledOnnxPack(pack: DetectedTtsPack): InstalledOnnxPack? {
        val modelId = pack.modelId ?: return null
        val precision = pack.precision ?: return null
        return InstalledOnnxPack(
            modelId = modelId,
            precision = precision,
            rootDir = pack.rootDir,
            estimatedSizeMb = pack.estimatedSizeMb ?: -1,
            runtimeFamily = pack.runtimeFamily,
            kokoroVoice = pack.voices.firstOrNull()?.id,
        )
    }
}
