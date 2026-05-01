package com.soll.domain.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SileroJitEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    private var isPaused = false
    private var currentSentenceIndex = 0
    private var sentences: List<SentenceInfo> = emptyList()
    private var speechRate = 1.0f
    private var sampleRate = 22050

    companion object {
        private const val MODEL_BASE_DIR = "piper_ru"

        // Available Russian voices from sherpa-onnx releases
        val VOICES = listOf(
            Voice("irina", "Ирина (ж)", "vits-piper-ru_RU-irina-medium"),
            Voice("denis", "Денис (м)", "vits-piper-ru_RU-denis-medium"),
            Voice("dmitri", "Дмитрий (м)", "vits-piper-ru_RU-dmitri-medium"),
            Voice("ruslan", "Руслан (м)", "vits-piper-ru_RU-ruslan-medium"),
        )

        private fun archiveUrl(archiveName: String) =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName.tar.bz2"
    }

    data class Voice(val id: String, val label: String, val archiveName: String) {
        val onnxFilename get() = "ru_RU-$id-medium.onnx"
    }

    private var currentVoice = VOICES[0]

    data class SentenceInfo(val text: String, val startOffset: Int, val endOffset: Int)

    fun isModelDownloaded(): Boolean = isVoiceDownloaded(currentVoice)

    private fun isVoiceDownloaded(voice: Voice): Boolean {
        val dir = File(context.filesDir, MODEL_BASE_DIR)
        val voiceDir = File(dir, voice.archiveName)
        return voiceDir.exists() &&
                File(voiceDir, voice.onnxFilename).let { it.exists() && it.length() > 1_000_000 } &&
                File(voiceDir, "tokens.txt").exists() &&
                File(voiceDir, "espeak-ng-data").exists()
    }

    fun setVoice(voiceId: String) {
        val voice = VOICES.find { it.id == voiceId } ?: VOICES[0]
        if (voice.id != currentVoice.id) {
            currentVoice = voice
            // Will need reinit if voice changes
            if (_isReady.value) {
                tts?.release()
                tts = null
                _isReady.value = false
            }
        }
    }

    // Kept for API compatibility
    fun setUseV5(enabled: Boolean) {}
    fun setV5SpeakerId(id: Int) {}

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseDir = File(context.filesDir, MODEL_BASE_DIR)
            baseDir.mkdirs()

            // Clean up old files
            listOf("silero_v1_kseniya_16000.jit", "silero_v5_cis_base.jit",
                "silero_tts_v3_1_ru.pt", "silero_tts_v4_ru.pt", "piper_ru_irina_medium.onnx"
            ).forEach { File(context.filesDir, it).let { f -> if (f.exists()) f.delete() } }
            // Clean old directory name
            File(context.filesDir, "piper_ru_irina").let { if (it.exists()) it.deleteRecursively() }

            val voice = currentVoice
            if (!isVoiceDownloaded(voice)) {
                Timber.d("Downloading voice: ${voice.label} (${voice.archiveName})")
                val success = downloadAndExtractArchive(baseDir, voice)
                if (!success) {
                    Timber.e("Failed to download voice model")
                    return@withContext false
                }
            }

            val voiceDir = File(baseDir, voice.archiveName)
            val modelPath = File(voiceDir, voice.onnxFilename).absolutePath
            val tokensPath = File(voiceDir, "tokens.txt").absolutePath
            val dataDir = File(voiceDir, "espeak-ng-data").absolutePath

            Timber.d("Initializing sherpa-onnx TTS: model=$modelPath")

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelPath,
                        tokens = tokensPath,
                        dataDir = dataDir,
                    ),
                    numThreads = 2,
                    debug = false,
                )
            )
            tts = OfflineTts(config = config)
            sampleRate = tts?.sampleRate() ?: 22050
            _isReady.value = true
            Timber.d("Sherpa-ONNX TTS initialized, sampleRate=$sampleRate")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TTS")
            _isReady.value = false
            false
        }
    }

    private suspend fun downloadAndExtractArchive(baseDir: File, voice: Voice): Boolean = withContext(Dispatchers.IO) {
        val url = archiveUrl(voice.archiveName)
        val archiveFile = File(baseDir, "${voice.archiveName}.tar.bz2")
        _downloadProgress.value = 0f

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .followRedirects(true).build()

        if (!downloadFile(client, url, archiveFile)) {
            _downloadProgress.value = null
            return@withContext false
        }

        _downloadProgress.value = 0.9f
        Timber.d("Extracting ${voice.archiveName}...")
        extractTarBz2(archiveFile, baseDir)
        archiveFile.delete()
        _downloadProgress.value = null

        isVoiceDownloaded(voice)
    }

    private fun downloadFile(client: OkHttpClient, url: String, target: File): Boolean {
        return try {
            if (target.exists() && target.length() > 0) return true

            for (attempt in 1..3) {
                try {
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Timber.w("Download failed ($attempt/3): ${response.code} for ${target.name}")
                        continue
                    }
                    val body = response.body ?: continue
                    val tempFile = File(target.parentFile, "${target.name}.tmp")
                    FileOutputStream(tempFile).use { fos ->
                        body.byteStream().use { it.copyTo(fos) }
                    }
                    tempFile.renameTo(target)
                    Timber.d("Downloaded ${target.name}: ${target.length()} bytes")
                    return true
                } catch (e: Exception) {
                    Timber.w("Download attempt $attempt failed for ${target.name}: ${e.message}")
                    Thread.sleep(2000L * attempt)
                }
            }
            false
        } catch (e: Exception) {
            Timber.e(e, "Download failed: ${target.name}")
            false
        }
    }

    private fun extractTarBz2(archive: File, destDir: File) {
        try {
            val pb = ProcessBuilder("tar", "xjf", archive.absolutePath, "-C", destDir.absolutePath)
            pb.redirectErrorStream(true)
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Timber.d("Extracted espeak-ng-data successfully")
            } else {
                Timber.e("tar extraction failed ($exitCode): $output")
            }
        } catch (e: Exception) {
            Timber.e(e, "tar extraction failed")
        }
    }

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit = {}) = coroutineScope {
        stop()
        sentences = splitIntoSentences(text)
        currentSentenceIndex = 0
        isPaused = false
        _isSpeaking.value = true
        Timber.d("speakChapter: ${sentences.size} sentences")

        playbackJob = launch(Dispatchers.IO) {
            try {
                while (currentSentenceIndex < sentences.size && isActive && !isPaused) {
                    val sentence = sentences[currentSentenceIndex]
                    _currentWordRange.value = IntRange(sentence.startOffset, sentence.endOffset)

                    val audio = generateAudio(sentence.text)
                    if (audio != null && isActive && !isPaused) {
                        val wordJob = launch { trackWordsInSentence(sentence, audio.size) }
                        playAudioBlocking(audio)
                        wordJob.cancel()
                    }
                    if (!isPaused) currentSentenceIndex++
                }
                if (currentSentenceIndex >= sentences.size && !isPaused) {
                    _isSpeaking.value = false
                    _currentWordRange.value = null
                    withContext(Dispatchers.Main) { onChapterFinished() }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) Timber.e(e, "Playback error")
                _isSpeaking.value = false
                _currentWordRange.value = null
            }
        }
    }

    private fun generateAudio(text: String): FloatArray? {
        val engine = tts ?: return null
        if (text.isBlank()) return null

        return try {
            val speed = 1.0f / speechRate // sherpa-onnx: speed < 1 = faster
            val audio = engine.generate(text = text, sid = 0, speed = speed)
            Timber.d("Generated ${audio.samples.size} samples for: ${text.take(30)}")
            audio.samples
        } catch (e: Exception) {
            Timber.e(e, "TTS generation failed: ${text.take(40)}")
            null
        }
    }

    private suspend fun trackWordsInSentence(sentence: SentenceInfo, audioSamples: Int) {
        val durationMs = (audioSamples.toFloat() / sampleRate * 1000).toLong()
        val text = sentence.text
        if (text.isBlank()) return

        val words = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length) break
            val start = i
            while (i < text.length && !text[i].isWhitespace()) i++
            words.add(IntRange(start, i))
        }
        if (words.isEmpty()) return

        val totalChars = words.sumOf { it.last - it.first }
        for (word in words) {
            val wordDurationMs = (durationMs * (word.last - word.first) / totalChars).coerceAtLeast(50)
            _currentWordRange.value = IntRange(sentence.startOffset + word.first, sentence.startOffset + word.last)
            delay(wordDurationMs)
        }
    }

    private fun playAudioBlocking(audioData: FloatArray) {
        val shorts = ShortArray(audioData.size) { i ->
            (audioData[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(shorts.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track
        track.write(shorts, 0, shorts.size)
        track.play()
        Thread.sleep((shorts.size * 1000L / sampleRate) + 100)
        try { track.stop(); track.release() } catch (_: Exception) {}
    }

    fun pause() {
        isPaused = true; playbackJob?.cancel()
        try { audioTrack?.stop() } catch (_: Exception) {}
        _isSpeaking.value = false; _currentWordRange.value = null
    }

    fun stop() {
        isPaused = false; playbackJob?.cancel(); playbackJob = null
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null; currentSentenceIndex = 0; sentences = emptyList()
        _isSpeaking.value = false; _currentWordRange.value = null
    }

    fun setSpeechRate(rate: Float) { speechRate = rate.coerceIn(0.5f, 2.0f) }

    fun shutdown() { stop(); tts?.release(); tts = null; _isReady.value = false }

    private fun splitIntoSentences(text: String): List<SentenceInfo> {
        val result = mutableListOf<SentenceInfo>()
        val pattern = Regex("""[.!?]+[\s\n]+|[.!?]+$|\n\n+""")
        var lastEnd = 0
        pattern.findAll(text).forEach { match ->
            val end = match.range.last + 1
            val t = text.substring(lastEnd, end).trim()
            if (t.isNotBlank()) result.add(SentenceInfo(t, lastEnd, end))
            lastEnd = end
        }
        if (lastEnd < text.length) {
            val t = text.substring(lastEnd).trim()
            if (t.isNotBlank()) result.add(SentenceInfo(t, lastEnd, text.length))
        }
        if (result.isEmpty() && text.isNotBlank()) result.add(SentenceInfo(text.trim(), 0, text.length))
        return result
    }
}
