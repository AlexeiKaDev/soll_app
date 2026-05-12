package com.soll.domain.tts.chatterbox

import android.annotation.SuppressLint
import android.util.Half
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object ChatterboxWaveReader {
    private const val RIFF = "RIFF"
    private const val WAVE = "WAVE"
    private const val FMT = "fmt "
    private const val DATA = "data"
    private const val WAV_FORMAT_PCM = 0x0001
    private const val WAV_FORMAT_IEEE_FLOAT = 0x0003
    private const val WAV_FORMAT_EXTENSIBLE = 0xFFFE

    fun readMonoFloat(file: File, targetSampleRate: Int = 24_000): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "WAV слишком маленький: ${file.absolutePath}" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(readAscii(buffer, 0, 4) == RIFF) { "Не RIFF WAV: ${file.absolutePath}" }
        require(readAscii(buffer, 8, 4) == WAVE) { "Не WAVE: ${file.absolutePath}" }

        var fmtOffset = -1
        var fmtSize = -1
        var dataOffset = -1
        var dataSize = -1
        var cursor = 12
        while (cursor + 8 <= bytes.size) {
            val chunkId = readAscii(buffer, cursor, 4)
            val chunkSize = buffer.getInt(cursor + 4)
            val chunkDataOffset = cursor + 8
            when (chunkId) {
                FMT -> {
                    fmtOffset = chunkDataOffset
                    fmtSize = chunkSize
                }
                DATA -> {
                    dataOffset = chunkDataOffset
                    dataSize = chunkSize
                }
            }
            cursor = chunkDataOffset + chunkSize + (chunkSize and 1)
        }

        require(fmtOffset >= 0 && dataOffset >= 0) { "WAV без fmt/data: ${file.absolutePath}" }

        var audioFormat = buffer.getShort(fmtOffset).toInt() and 0xFFFF
        val channels = buffer.getShort(fmtOffset + 2).toInt() and 0xFFFF
        val sampleRate = buffer.getInt(fmtOffset + 4)
        val bitsPerSample = buffer.getShort(fmtOffset + 14).toInt() and 0xFFFF
        if (audioFormat == WAV_FORMAT_EXTENSIBLE && fmtSize >= 40) {
            audioFormat = buffer.getShort(fmtOffset + 24).toInt() and 0xFFFF
        }

        val mono = when (audioFormat) {
            WAV_FORMAT_PCM -> decodePcm(bytes, dataOffset, dataSize, channels, bitsPerSample)
            WAV_FORMAT_IEEE_FLOAT -> decodeFloat(bytes, dataOffset, dataSize, channels, bitsPerSample)
            else -> error("Неподдерживаемый WAV format=$audioFormat bits=$bitsPerSample")
        }
        return if (sampleRate == targetSampleRate) mono else resampleLinear(mono, sampleRate, targetSampleRate)
    }

    private fun decodePcm(
        bytes: ByteArray,
        offset: Int,
        size: Int,
        channels: Int,
        bitsPerSample: Int,
    ): FloatArray {
        val bytesPerSample = bitsPerSample / 8
        require(bytesPerSample in setOf(1, 2, 3, 4)) { "PCM bits=$bitsPerSample не поддержаны" }
        val frameSize = bytesPerSample * channels
        val frameCount = size / frameSize
        val out = FloatArray(frameCount)
        var cursor = offset
        for (frame in 0 until frameCount) {
            var mixed = 0f
            for (channel in 0 until channels) {
                val sample = when (bytesPerSample) {
                    1 -> ((bytes[cursor].toInt() and 0xFF) - 128) / 128f
                    2 -> {
                        val value = ((bytes[cursor + 1].toInt() shl 8) or (bytes[cursor].toInt() and 0xFF)).toShort()
                        value / 32768f
                    }
                    3 -> {
                        val b0 = bytes[cursor].toInt() and 0xFF
                        val b1 = bytes[cursor + 1].toInt() and 0xFF
                        val b2 = bytes[cursor + 2].toInt()
                        val value = (b2 shl 16) or (b1 shl 8) or b0
                        (if (value and 0x800000 != 0) value or -0x1000000 else value) / 8_388_608f
                    }
                    else -> {
                        val value = ByteBuffer.wrap(bytes, cursor, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        value / 2_147_483_648f
                    }
                }
                mixed += sample
                cursor += bytesPerSample
            }
            out[frame] = mixed / channels.coerceAtLeast(1)
        }
        return out
    }

    @SuppressLint("HalfFloat")
    private fun decodeFloat(
        bytes: ByteArray,
        offset: Int,
        size: Int,
        channels: Int,
        bitsPerSample: Int,
    ): FloatArray {
        val bytesPerSample = bitsPerSample / 8
        require(bytesPerSample in setOf(2, 4)) { "FLOAT WAV bits=$bitsPerSample не поддержаны" }
        val frameSize = bytesPerSample * channels
        val frameCount = size / frameSize
        val out = FloatArray(frameCount)
        var cursor = offset
        for (frame in 0 until frameCount) {
            var mixed = 0f
            for (channel in 0 until channels) {
                val sample = if (bytesPerSample == 2) {
                    val halfBits = ((bytes[cursor + 1].toInt() shl 8) or (bytes[cursor].toInt() and 0xFF)).toShort()
                    Half.toFloat(halfBits)
                } else {
                    ByteBuffer.wrap(bytes, cursor, 4).order(ByteOrder.LITTLE_ENDIAN).float
                }
                mixed += sample
                cursor += bytesPerSample
            }
            out[frame] = mixed / channels.coerceAtLeast(1)
        }
        return out
    }

    private fun resampleLinear(
        input: FloatArray,
        fromSampleRate: Int,
        toSampleRate: Int,
    ): FloatArray {
        if (input.isEmpty() || fromSampleRate <= 0 || toSampleRate <= 0 || fromSampleRate == toSampleRate) {
            return input
        }
        val ratio = toSampleRate.toDouble() / fromSampleRate.toDouble()
        val newSize = (input.size * ratio).roundToInt().coerceAtLeast(1)
        val out = FloatArray(newSize)
        for (i in 0 until newSize) {
            val src = i / ratio
            val left = src.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceIn(0, input.lastIndex)
            val frac = (src - left).toFloat()
            out[i] = input[left] * (1f - frac) + input[right] * frac
        }
        return out
    }

    private fun readAscii(buffer: ByteBuffer, offset: Int, count: Int): String {
        val bytes = ByteArray(count)
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        dup.position(offset)
        dup.get(bytes)
        return bytes.decodeToString()
    }
}
