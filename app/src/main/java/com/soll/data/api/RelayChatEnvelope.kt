package com.soll.data.api

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

internal fun decryptRelayChatContent(envelope: SecurePayloadEnvelopeRequest, pairingSecret: String, deviceId: String): String {
    require(envelope.algorithm == "AES-256-GCM" && envelope.keyId == deviceId && pairingSecret.isNotBlank())
    require(envelope.aad in setOf("POST /api/v1/chat/turn", "POST /api/v1/chat/messages"))
    val key = MessageDigest.getInstance("SHA-256").digest(pairingSecret.toByteArray(Charsets.UTF_8))
    val nonce = Base64.getDecoder().decode(envelope.nonce)
    require(nonce.size == 12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(envelope.aad.toByteArray(Charsets.UTF_8))
    val body = JSONObject(String(cipher.doFinal(Base64.getDecoder().decode(envelope.ciphertext)), Charsets.UTF_8))
    return body.getString("content")
}
