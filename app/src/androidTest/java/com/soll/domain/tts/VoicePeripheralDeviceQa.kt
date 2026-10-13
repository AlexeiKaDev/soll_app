package com.soll.domain.tts

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.soll.di.AppModule
import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit device QA only: no credentials in arguments, logs, or the test APK. */
class VoicePeripheralDeviceQa {
    @Test fun pairedPhoneCanFetchTheExactServerReply() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runPhysicalVoiceQa") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppModule.provideEncryptedSharedPreferences(context)
        val token = preferences.getString("soll_access_token", "").orEmpty()
        val server = preferences.getString("soll_server_url", "").orEmpty()
        val prefix = preferences.getString("soll_api_path_prefix", "").orEmpty()
        val client = preferences.getString("soll_remote_client_id", "").orEmpty()
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
        Log.i("SollVoicePhysicalQa", "endpoint=${URI(server).host}; client=$client; tokenPresent=${token.isNotBlank()}; tokenFingerprint=$fingerprint")
        assertEquals(args.getString("expectedTokenFingerprint"), fingerprint)
        val expectedId = requireNotNull(args.getString("expectedReplyId")).toLong()
        val messages = withContext(Dispatchers.IO) {
            val connection = URL(server.trimEnd('/') + "/" + prefix.trim('/') + "/chat/sessions/soll-main?after_id=${expectedId - 1}&limit=2").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-Soll-Client", client)
                assertEquals(200, connection.responseCode)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("messages")
            } finally { connection.disconnect() }
        }
        assertTrue("Exact completed voice QA reply must reach the paired phone", (0 until messages.length()).any {
            val message = messages.getJSONObject(it)
            message.optLong("id") == expectedId && message.optString("content") == "Голос работает."
        })
    }
}
