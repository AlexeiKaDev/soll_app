package com.soll.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnRequestTest {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ChatTurnRequest::class.java)

    @Test
    fun `voice safety flags serialize with canonical server names`() {
        val json = adapter.toJson(
            ChatTurnRequest(
                content = "Что важного сегодня?",
                metadata = mapOf("source" to "android_voice"),
                taskIntake = false,
                allowActions = false,
            )
        )

        assertTrue(json.contains("\"task_intake\":false"))
        assertTrue(json.contains("\"allow_actions\":false"))
    }

    @Test
    fun `android chat defaults fail closed`() {
        val json = adapter.toJson(ChatTurnRequest(content = "Обычный чат"))

        assertFalse(json.contains("\"task_intake\":true"))
        assertFalse(json.contains("\"allow_actions\":true"))
        assertTrue(json.contains("\"task_intake\":false"))
        assertTrue(json.contains("\"allow_actions\":false"))
    }
}
