package com.soll.data.api

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RelayChatEnvelopeTest {
    private fun envelope(): SecurePayloadEnvelopeRequest {
        val nonce = ByteArray(12) { it.toByte() }
        val key = MessageDigest.getInstance("SHA-256").digest("test-secret".toByteArray())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val aad = "POST /api/v1/chat/turn"
        cipher.updateAAD(aad.toByteArray())
        return SecurePayloadEnvelopeRequest(nonce = Base64.getEncoder().encodeToString(nonce),
            ciphertext = Base64.getEncoder().encodeToString(cipher.doFinal(JSONObject().put("content", "добавь задачу Купить кабель").toString().toByteArray())),
            aad = aad, keyId = "device-one")
    }
    @Test fun `encrypted relay history displays the owners original message`() {
        assertEquals("добавь задачу Купить кабель", decryptRelayChatContent(envelope(), "test-secret", "device-one"))
    }
    @Test fun `envelopes from another device or wrong context are rejected`() {
        assertTrue(runCatching { decryptRelayChatContent(envelope(), "test-secret", "other-device") }.isFailure)
        assertTrue(runCatching { decryptRelayChatContent(envelope().copy(aad = "POST /wrong"), "test-secret", "device-one") }.isFailure)
        assertTrue(runCatching { decryptRelayChatContent(envelope(), "wrong-secret", "device-one") }.isFailure)
    }
}
