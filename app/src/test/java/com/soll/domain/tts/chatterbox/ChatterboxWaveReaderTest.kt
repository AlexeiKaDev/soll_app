package com.soll.domain.tts.chatterbox

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ChatterboxWaveReaderTest {

    @Test
    fun readMonoFloat_readsPcm16Wave() {
        val wavFile = File.createTempFile("chatterbox-wave-", ".wav")
        try {
            wavFile.writeBytes(buildPcm16Wave(shortArrayOf(-32768, 0, 32767), sampleRate = 24_000))

            val samples = ChatterboxWaveReader.readMonoFloat(wavFile, targetSampleRate = 24_000)

            assertEquals(3, samples.size)
            assertEquals(-1.0f, samples[0], 0.0001f)
            assertEquals(0.0f, samples[1], 0.0001f)
            assertEquals(32767f / 32768f, samples[2], 0.0001f)
        } finally {
            wavFile.delete()
        }
    }

    private fun buildPcm16Wave(
        samples: ShortArray,
        sampleRate: Int,
    ): ByteArray {
        val dataSize = samples.size * 2
        val totalSize = 44 + dataSize
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize - 8)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        samples.forEach { sample -> buffer.putShort(sample) }
        return buffer.array()
    }
}
