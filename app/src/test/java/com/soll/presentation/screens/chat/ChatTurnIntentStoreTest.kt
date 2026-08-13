package com.soll.presentation.screens.chat

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatTurnIntentStoreTest {
    @Test
    fun `same owner intent keeps client turn id across retry and recreation`() {
        val state = SavedStateHandle()
        val first = ChatTurnIntentStore(
            savedStateHandle = state,
            idFactory = { "android-chat:first" },
            nonceSeedFactory = { "android-chat-nonce:first" },
        )

        val initial = first.resolve("  Проверь новости  ", "soll-main")
        val retry = first.resolve("Проверь новости", "soll-main")
        val recreated = ChatTurnIntentStore(
            savedStateHandle = state,
            idFactory = { "android-chat:must-not-run" },
            nonceSeedFactory = { "android-chat-nonce:must-not-run" },
        ).restore()

        assertEquals("android-chat:first", initial.clientTurnId)
        assertEquals("android-chat-nonce:first", initial.encryptionNonceSeed)
        assertEquals(initial, retry)
        assertEquals(initial, recreated)
    }

    @Test
    fun `editing or completing intent prevents accidental id reuse`() {
        val ids = ArrayDeque(listOf("android-chat:first", "android-chat:second"))
        val state = SavedStateHandle()
        val store = ChatTurnIntentStore(
            savedStateHandle = state,
            idFactory = { ids.removeFirst() },
            nonceSeedFactory = { "android-chat-nonce:${ids.size}" },
        )
        val initial = store.resolve("Первый запрос", "soll-main")

        store.invalidateIfContentChanged("Исправленный запрос")
        assertNull(store.restore())

        val edited = store.resolve("Исправленный запрос", "soll-main")
        assertNotEquals(initial.clientTurnId, edited.clientTurnId)
        store.complete(edited.clientTurnId)
        assertNull(store.restore())
    }

    @Test
    fun `transport failure leaves the same id available for explicit retry`() {
        val state = SavedStateHandle()
        val store = ChatTurnIntentStore(
            savedStateHandle = state,
            idFactory = { "android-chat:network-retry" },
            nonceSeedFactory = { "android-chat-nonce:network-retry" },
        )
        val sent = store.resolve("Повтори после ошибки сети", "soll-main")

        // A transport failure deliberately does not call complete().
        val retried = ChatTurnIntentStore(
            savedStateHandle = state,
            idFactory = { "android-chat:unexpected" },
            nonceSeedFactory = { "android-chat-nonce:unexpected" },
        )
            .resolve("Повтори после ошибки сети", "soll-main")

        assertEquals(sent.clientTurnId, retried.clientTurnId)
        assertEquals(sent.encryptionNonceSeed, retried.encryptionNonceSeed)
        assertEquals("android-chat:network-retry", retried.clientTurnId)
    }
}
