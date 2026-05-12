package com.soll.presentation.screens.tools.breathing

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Короткие сигналы на смену фазы (без тяжёлых ассетов): ToneGenerator + лёгкая вибрация.
 */
@Singleton
class BreathingAudioFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tone: ToneGenerator? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun toneGenerator(): ToneGenerator {
        tone?.let { return it }
        return ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            .also { tone = it }
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        tone?.release()
        tone = null
    }

    fun playSessionStart() {
        val tg = toneGenerator()
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
        mainHandler.postDelayed({ tg.startTone(ToneGenerator.TONE_PROP_BEEP, 55) }, 160)
        vibrate(22)
    }

    /** Смена вдох / выдох в серии дыханий */
    fun playBreathPhase(isInhale: Boolean) {
        val tg = toneGenerator()
        if (isInhale) {
            tg.startTone(ToneGenerator.TONE_DTMF_1, 65)
        } else {
            tg.startTone(ToneGenerator.TONE_DTMF_4, 65)
        }
        vibrate(18)
    }

    fun playHoldPhaseStart() {
        toneGenerator().startTone(ToneGenerator.TONE_DTMF_S, 110)
        vibrate(35)
    }

    fun playRecoveryPhaseStart() {
        toneGenerator().startTone(ToneGenerator.TONE_PROP_ACK, 85)
        vibrate(25)
    }

    fun playRoundCompleteChime() {
        val tg = toneGenerator()
        tg.startTone(ToneGenerator.TONE_DTMF_2, 55)
        mainHandler.postDelayed({ tg.startTone(ToneGenerator.TONE_DTMF_5, 75) }, 140)
        vibrate(22)
    }

    fun playSessionComplete() {
        val tg = toneGenerator()
        tg.startTone(ToneGenerator.TONE_DTMF_1, 70)
        mainHandler.postDelayed({ tg.startTone(ToneGenerator.TONE_DTMF_3, 70) }, 130)
        mainHandler.postDelayed({ tg.startTone(ToneGenerator.TONE_DTMF_5, 100) }, 270)
        vibrate(40)
    }

    private fun vibrate(ms: Long) {
        val v = vibrator() ?: return
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
