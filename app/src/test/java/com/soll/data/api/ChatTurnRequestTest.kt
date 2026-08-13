package com.soll.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.soll.domain.voice.VoiceAssistantTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET

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

    @Test
    fun `voice request id becomes canonical top level client turn id`() {
        val turn = VoiceAssistantTurn.create(
            transcript = "Что важного сегодня?",
            requestId = "voice-turn-1",
        )
        val clientTurnId = stableChatClientTurnId(turn.metadata)
        val json = adapter.toJson(
            ChatTurnRequest(
                content = turn.content,
                metadata = turn.metadata,
                clientTurnId = clientTurnId,
            )
        )

        assertEquals("voice-turn-1", clientTurnId)
        assertTrue(json.contains("\"client_turn_id\":\"voice-turn-1\""))
        assertTrue(json.contains("\"request_id\":\"voice-turn-1\""))
    }

    @Test
    fun `legacy chat without stable metadata id keeps old wire contract`() {
        val metadata = mapOf<String, Any?>("source" to "android_app")
        val clientTurnId = stableChatClientTurnId(metadata)
        val json = adapter.toJson(
            ChatTurnRequest(
                content = "Обычный чат",
                metadata = metadata,
                clientTurnId = clientTurnId,
            )
        )

        assertNull(clientTurnId)
        assertFalse(json.contains("client_turn_id"))
    }

    @Test
    fun `explicit client turn id takes precedence without generating a replacement`() {
        val metadata = mapOf<String, Any?>(
            "client_turn_id" to "stable-client-turn",
            "request_id" to "older-request-id",
        )

        assertEquals("stable-client-turn", stableChatClientTurnId(metadata))
        assertNull(stableChatClientTurnId(mapOf("request_id" to 42)))
    }

    @Test
    fun `queued and failed turn status fields survive wire decoding`() {
        val responseAdapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(ChatTurnResponse::class.java)

        val queued = requireNotNull(
            responseAdapter.fromJson(
                """{"turn_id":"turn-1","client_turn_id":"android-chat:1","status":"queued","final":false}"""
            )
        )
        val failed = requireNotNull(
            responseAdapter.fromJson(
                """{"turn_id":"turn-1","client_turn_id":"android-chat:1","status":"failed","final":true,"error":{"code":"core_failed","message":"Core failed"}}"""
            )
        )

        assertEquals("turn-1", queued.turnId)
        assertEquals("android-chat:1", queued.clientTurnId)
        assertEquals("queued", queued.status)
        assertFalse(queued.final)
        assertEquals("failed", failed.status)
        assertTrue(failed.final)
        assertEquals("core_failed", failed.error?.code)
        assertEquals("Core failed", failed.error?.message)
    }

    @Test
    fun `turn status uses deployed authenticated get route`() {
        val method = SollApiService::class.java.methods.single { it.name == "getChatTurnStatus" }
        val route = requireNotNull(method.getAnnotation(GET::class.java))

        assertEquals("api/v1/chat/turns/{turn_id}", route.value)
    }
}
