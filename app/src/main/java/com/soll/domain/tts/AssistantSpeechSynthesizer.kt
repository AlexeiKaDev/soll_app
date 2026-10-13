package com.soll.domain.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.soll.domain.soll.isSollVoiceWav
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Chat-only synthesis: playback always belongs to AssistantVoicePlayer. */
class AssistantSpeechSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun synthesize(text: String): List<ByteArray> = withContext(Dispatchers.Main) {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Текст для озвучивания пуст" }
        require(clean.length <= 16_000) { "Ответ слишком длинный для озвучивания" }
        val ready = CompletableDeferred<Int>()
        val engine = TextToSpeech(context) { status -> ready.complete(status) }
        val files = mutableListOf<File>()
        try {
            withTimeout(15_000) {
                check(ready.await() == TextToSpeech.SUCCESS) { "Не удалось запустить озвучивание ответа" }
            }
            val language = engine.setLanguage(Locale.forLanguageTag("ru-RU"))
            check(language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED) {
                "Русский голос недоступен на устройстве"
            }
            val audio = mutableListOf<ByteArray>()
            for (chunk in assistantSpeechChunks(clean, TextToSpeech.getMaxSpeechInputLength())) {
                val completed = CompletableDeferred<Unit>()
                val utteranceId = UUID.randomUUID().toString()
                val file = withContext(Dispatchers.IO) {
                    File.createTempFile("soll-assistant-speech-", ".wav", context.cacheDir)
                }
                files += file
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) {
                        if (id == utteranceId) completed.complete(Unit)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) = fail(id)
                    override fun onError(id: String?, errorCode: Int) = fail(id)
                    private fun fail(id: String?) {
                        if (id == utteranceId) completed.completeExceptionally(IllegalStateException("Не удалось озвучить ответ"))
                    }
                })
                check(engine.synthesizeToFile(chunk, null, file, utteranceId) == TextToSpeech.SUCCESS) {
                    "Не удалось запустить синтез ответа"
                }
                withTimeout(30_000) { completed.await() }
                val bytes = withContext(Dispatchers.IO) {
                    check(file.length() in 44..8_000_000) { "Голосовой ответ повреждён" }
                    file.readBytes()
                }
                check(bytes.isSollVoiceWav()) { "Голосовой ответ повреждён" }
                audio += bytes
            }
            audio
        } finally {
            engine.stop()
            engine.shutdown()
            // Only files created by this bounded synthesis operation.
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                files.forEach(File::delete)
            }
        }
    }
}

internal fun assistantSpeechChunks(text: String, limit: Int): List<String> {
    require(limit > 1)
    var remaining = text.trim()
    val chunks = mutableListOf<String>()
    while (remaining.length > limit) {
        val separator = remaining.lastIndexOf(' ', limit - 1)
        val boundary = if (separator >= limit / 2) separator else limit
        chunks += remaining.substring(0, boundary)
        remaining = remaining.substring(boundary).trimStart()
    }
    if (remaining.isNotEmpty()) chunks += remaining
    return chunks
}
