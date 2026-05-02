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
import kotlin.math.roundToInt

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

    private var sherpaNumThreads: Int = TtsBookPerformanceProfile.sherpaNumThreads(
        TtsBookPerformanceProfile.BALANCED,
        Runtime.getRuntime().availableProcessors(),
    )
    private var mergeShortThreshold = 220
    private var mergeTotalCap = 360

    fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        val (a, b) = TtsBookPerformanceProfile.chunkMergeLimits(profile)
        mergeShortThreshold = a
        mergeTotalCap = b
        val n = TtsBookPerformanceProfile.sherpaNumThreads(
            profile,
            Runtime.getRuntime().availableProcessors(),
        )
        applySherpaNumThreadsInternal(n, rebuild = true)
    }

    fun getSherpaNumThreads(): Int = sherpaNumThreads

    fun applySherpaNumThreads(value: Float) {
        applySherpaNumThreadsInternal(value.roundToInt(), rebuild = true)
    }

    private fun applySherpaNumThreadsInternal(n: Int, rebuild: Boolean) {
        val threads = n.coerceIn(1, 4)
        if (threads == sherpaNumThreads && tts != null) return
        sherpaNumThreads = threads
        if (rebuild) {
            tts?.release()
            tts = null
            _isReady.value = false
        }
    }

    companion object {
        private const val MODEL_DIR = "piper_ru_irina"

        // Sherpa-onnx pre-converted Piper model (has embedded metadata)
        private const val MODEL_ARCHIVE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2"
    }

    data class SentenceInfo(val text: String, val startOffset: Int, val endOffset: Int)

    fun isModelDownloaded(): Boolean {
        val dir = File(context.filesDir, MODEL_DIR)
        // Look for the extracted model directory from sherpa-onnx archive
        val extractedDir = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("vits-piper") }
        if (extractedDir != null) {
            val model = File(extractedDir, "ru_RU-irina-medium.onnx")
            val tokens = File(extractedDir, "tokens.txt")
            val espeakDir = File(extractedDir, "espeak-ng-data")
            return model.exists() && model.length() > 1_000_000 && tokens.exists() && espeakDir.exists()
        }
        return false
    }

    private fun getModelDir(): File? {
        val dir = File(context.filesDir, MODEL_DIR)
        return dir.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("vits-piper") }
    }

    // These are no-ops now, kept for API compatibility
    fun setUseV5(enabled: Boolean) {}
    fun setV5SpeakerId(id: Int) {}

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, MODEL_DIR)
            dir.mkdirs()

            // Clean up old files
            listOf("silero_v1_kseniya_16000.jit", "silero_v5_cis_base.jit",
                "silero_tts_v3_1_ru.pt", "silero_tts_v4_ru.pt", "piper_ru_irina_medium.onnx"
            ).forEach { File(context.filesDir, it).let { f -> if (f.exists()) f.delete() } }

            if (!isModelDownloaded()) {
                Timber.d("Downloading Piper Russian model archive...")
                val success = downloadAndExtractArchive(dir)
                if (!success) {
                    Timber.e("Failed to download model")
                    return@withContext false
                }
            }

            val modelDir = getModelDir()
            if (modelDir == null) {
                Timber.e("Model directory not found after download")
                return@withContext false
            }

            val modelPath = File(modelDir, "ru_RU-irina-medium.onnx").absolutePath
            val tokensPath = File(modelDir, "tokens.txt").absolutePath
            val dataDir = File(modelDir, "espeak-ng-data").absolutePath

            Timber.d("Initializing sherpa-onnx TTS: model=$modelPath")

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelPath,
                        tokens = tokensPath,
                        dataDir = dataDir,
                    ),
                    numThreads = sherpaNumThreads,
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

    private suspend fun downloadAndExtractArchive(dir: File): Boolean = withContext(Dispatchers.IO) {
        val archiveFile = File(dir, "model.tar.bz2")
        _downloadProgress.value = 0f

        // Download archive
        if (!downloadFile(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.MINUTES)
                    .followRedirects(true).build(),
                MODEL_ARCHIVE_URL, archiveFile
            )
        ) {
            _downloadProgress.value = null
            return@withContext false
        }

        _downloadProgress.value = 0.9f
        Timber.d("Extracting model archive...")
        extractTarBz2(archiveFile, dir)
        archiveFile.delete()
        _downloadProgress.value = null

        isModelDownloaded()
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
        Thread.sleep(shorts.size * 1000L / sampleRate)
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
        return mergeNearbySentences(result)
    }

    private fun mergeNearbySentences(sentences: List<SentenceInfo>): List<SentenceInfo> {
        if (sentences.size <= 1) return sentences
        val merged = mutableListOf<SentenceInfo>()
        var current = sentences.first()
        val maxShort = mergeShortThreshold
        val maxTotal = mergeTotalCap
        for (i in 1 until sentences.size) {
            val next = sentences[i]
            val shouldMerge = current.text.length < maxShort && next.text.length < maxShort &&
                (current.text.length + next.text.length) < maxTotal
            current = if (shouldMerge) {
                SentenceInfo(
                    text = "${current.text} ${next.text}".trim(),
                    startOffset = current.startOffset,
                    endOffset = next.endOffset,
                )
            } else {
                merged.add(current)
                next
            }
        }
        merged.add(current)
        return merged
    }
}
