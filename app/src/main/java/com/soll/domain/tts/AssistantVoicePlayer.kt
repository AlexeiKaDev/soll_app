package com.soll.domain.tts

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.soll.domain.soll.isSollVoiceWav
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class AssistantVoicePlaybackPhase {
    IDLE,
    PREPARING,
    PLAYING,
    ERROR,
}

data class AssistantVoicePlaybackState(
    val messageId: Long? = null,
    val phase: AssistantVoicePlaybackPhase = AssistantVoicePlaybackPhase.IDLE,
    val error: String? = null,
)

@Singleton
class AssistantVoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(AssistantVoicePlaybackState())
    val state: StateFlow<AssistantVoicePlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    suspend fun play(messageId: Long, audio: ByteArray): Boolean {
        releasePlayer()
        if (!audio.isSollVoiceWav()) {
            _state.value = AssistantVoicePlaybackState(
                messageId = messageId,
                phase = AssistantVoicePlaybackPhase.ERROR,
                error = "Голосовой ответ поврежден",
            )
            return false
        }
        val audioFile = runCatching {
            withContext(Dispatchers.IO) { cachedAudioFile(messageId, audio) }
        }.getOrElse { error ->
            _state.value = AssistantVoicePlaybackState(
                messageId = messageId,
                phase = AssistantVoicePlaybackPhase.ERROR,
                error = error.message ?: "Не удалось сохранить голосовой ответ",
            )
            return false
        }
        return runCatching {
            val nextPlayer = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_ASSISTANT)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                addListener(playbackListener)
                setMediaItem(MediaItem.fromUri(Uri.fromFile(audioFile)))
            }
            player = nextPlayer
            _state.value = AssistantVoicePlaybackState(
                messageId = messageId,
                phase = AssistantVoicePlaybackPhase.PREPARING,
            )
            nextPlayer.prepare()
            nextPlayer.playWhenReady = true
            true
        }.getOrElse { error ->
            releasePlayer()
            _state.value = AssistantVoicePlaybackState(
                messageId = messageId,
                phase = AssistantVoicePlaybackPhase.ERROR,
                error = error.message ?: "Не удалось воспроизвести голосовой ответ",
            )
            false
        }
    }

    fun stop() {
        releasePlayer()
        _state.value = AssistantVoicePlaybackState()
    }

    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val messageId = _state.value.messageId ?: return
            if (isPlaying) {
                _state.value = AssistantVoicePlaybackState(
                    messageId = messageId,
                    phase = AssistantVoicePlaybackPhase.PLAYING,
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                stop()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val messageId = _state.value.messageId
            releasePlayer()
            _state.value = AssistantVoicePlaybackState(
                messageId = messageId,
                phase = AssistantVoicePlaybackPhase.ERROR,
                error = error.message?.takeIf { it.isNotBlank() }
                    ?: "Не удалось воспроизвести голосовой ответ",
            )
        }
    }

    private fun cachedAudioFile(messageId: Long, audio: ByteArray): File {
        val directory = File(context.cacheDir, "assistant-voice").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(audio)
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(20)
        val target = File(directory, "message-$messageId-$digest.wav")
        if (!target.isFile || target.length() != audio.size.toLong()) {
            target.writeBytes(audio)
        }
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it != target }
            .sortedByDescending(File::lastModified)
            .drop(MAX_CACHED_ASSISTANT_VOICES - 1)
            .forEach(File::delete)
        return target
    }

    private fun releasePlayer() {
        val current = player
        player = null
        if (current != null) {
            current.removeListener(playbackListener)
            current.stop()
            current.release()
        }
    }
}

private const val MAX_CACHED_ASSISTANT_VOICES = 16
