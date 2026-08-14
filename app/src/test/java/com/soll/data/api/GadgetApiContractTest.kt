package com.soll.data.api

import com.soll.data.repository.rewriteSollApiUrl
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class GadgetApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SollApiService

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    request.newBuilder()
                        .url(rewriteSollApiUrl(request.url, "api/v1/soll"))
                        .build()
                )
            }
            .build()
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SollApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `empty snapshot is a bare authoritative array on prefixed route`() = runBlocking {
        server.enqueue(jsonResponse("[]"))

        val snapshots = api.getGadgets("Bearer test-only")
        val request = server.takeRequest()

        assertTrue(snapshots.isEmpty())
        assertEquals("/api/v1/soll/gadgets", request.path)
        assertEquals("Bearer test-only", request.getHeader("Authorization"))
    }

    @Test
    fun `empty claim is represented by json null`() = runBlocking {
        server.enqueue(jsonResponse("null"))

        val response = api.claimGadgetCommand(
            authorization = "Bearer test-only",
            gadgetId = "aquik-1",
            request = GadgetCommandClaimRequest(workerId = "android-main", leaseSeconds = 60),
        )
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())

        assertNull(response.body())
        assertEquals("/api/v1/soll/gadgets/aquik-1/commands/claim", request.path)
        assertEquals("android-main", body.getString("worker_id"))
        assertEquals(60, body.getInt("lease_seconds"))
    }

    @Test
    fun `core command extras are ignored while required transport fields survive`() = runBlocking {
        server.enqueue(jsonResponse(commandJson(status = "claimed")))

        val command = api.claimGadgetCommand(
            authorization = "Bearer test-only",
            gadgetId = "aquik-1",
            request = GadgetCommandClaimRequest(workerId = "android-main"),
        ).body()

        assertEquals("cmd-1", command?.id)
        assertEquals("aquik-1", command?.gadgetId)
        assertEquals("getSensors", command?.command)
        assertEquals("claimed", command?.status)
        assertEquals("read_only", command?.riskLevel)
    }

    @Test
    fun `ack and result use worker bound transport bodies`() = runBlocking {
        server.enqueue(jsonResponse(commandJson(status = "acked")))
        server.enqueue(jsonResponse(commandJson(status = "done")))

        api.ackGadgetCommand(
            authorization = "Bearer test-only",
            gadgetId = "aquik-1",
            commandId = "cmd-1",
            request = GadgetCommandAckRequest(workerId = "android-main"),
        )
        api.postGadgetCommandResult(
            authorization = "Bearer test-only",
            gadgetId = "aquik-1",
            commandId = "cmd-1",
            request = GadgetCommandResultRequest(
                success = true,
                payload = mapOf("temperature" to 24),
                workerId = "android-main",
            ),
        )

        val ack = server.takeRequest()
        val result = server.takeRequest()
        assertEquals("/api/v1/soll/gadgets/aquik-1/commands/cmd-1/ack", ack.path)
        assertEquals("android-main", JSONObject(ack.body.readUtf8()).getString("worker_id"))
        assertEquals("/api/v1/soll/gadgets/aquik-1/commands/cmd-1/result", result.path)
        val resultBody = JSONObject(result.body.readUtf8())
        assertTrue(resultBody.getBoolean("success"))
        assertEquals("android-main", resultBody.getString("worker_id"))
    }

    @Test
    fun `http auth route and server failures remain visible to worker`() = runBlocking {
        listOf(401, 403, 404, 503).forEach { statusCode ->
            server.enqueue(MockResponse().setResponseCode(statusCode).setBody("{}"))

            val response = api.claimGadgetCommand(
                authorization = "Bearer test-only",
                gadgetId = "aquik-1",
                request = GadgetCommandClaimRequest(workerId = "android-main"),
            )

            assertEquals(false, response.isSuccessful)
            assertEquals(statusCode, response.code())
        }
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private fun commandJson(status: String): String =
        """
        {
          "id":"cmd-1",
          "gadget_id":"aquik-1",
          "command":"getSensors",
          "params":{},
          "status":"$status",
          "reason":"",
          "result":{},
          "risk_level":"read_only",
          "approval_id":"",
          "attempt_count":1,
          "claimed_by":"android-main",
          "created_at":"2026-08-14T10:00:00Z",
          "expires_at":"2026-08-14T10:01:00Z",
          "claim_expires_at":"2026-08-14T10:01:00Z",
          "last_attempt_at":"2026-08-14T10:00:00Z",
          "acked_at":null,
          "completed_at":null
        }
        """.trimIndent()
}
