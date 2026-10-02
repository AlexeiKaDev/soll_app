package com.soll.data.api

import com.soll.data.repository.rewriteSollApiUrl
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * The production relay (sales.monolith-ost.com) wraps list responses from
 * GET /sources (and /sources/{id}/items) in `{"data": [...]}`, unlike the
 * local Soll server's bare-array responses, and 404s on the trailing-slash
 * GET sources route even though the POST variant accepts it. This test
 * pins both behaviors against the real Moshi/Retrofit stack so a regression
 * (e.g. someone reverting the adapter or re-adding the trailing slash)
 * would reintroduce the empty-Sources-tab bug silently fixed in this change.
 */
class RelayListEnvelopeAdapterTest {
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
            .add(RelayListEnvelopeAdapterFactory)
            .add(TolerantMapAdapterFactory)
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
    fun `bare array response from the local server still parses`() = runBlocking {
        server.enqueue(jsonResponse("""[{"id":"src-1","name":"Example"}]"""))

        val sources = api.listSources("Bearer test-only", scope = "project_soll")
        val request = server.takeRequest()

        assertEquals(1, sources.size)
        assertEquals("src-1", sources[0].id)
        assertEquals("/api/v1/soll/sources?scope=project_soll", request.path)
    }

    @Test
    fun `relay data envelope is unwrapped transparently`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":[{"id":"src-1","name":"Example"}]}"""))

        val sources = api.listSources("Bearer test-only", scope = "project_soll")

        assertEquals(1, sources.size)
        assertEquals("src-1", sources[0].id)
    }

    @Test
    fun `source items data envelope is unwrapped transparently`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":[{"item_id":"item-1","title":"Example item"}]}"""))

        val items = api.listSourceItems("Bearer test-only", sourceId = "src-1", limit = 20)

        assertEquals(1, items.size)
        assertEquals("item-1", items[0].itemId)
    }

    @Test
    fun `item with an empty-array link_preview parses instead of crashing the whole list`() = runBlocking {
        // The relay's normalizeSourceItem() defaults a missing link_preview
        // to PHP's [], which json_encode emits as a JSON array, not an
        // object. Real production payload observed for every item of a
        // real source (NeMo Guardrails Releases) that had no captured link
        // preview -- Moshi's default Map adapter throws on this, which
        // silently dropped the item's entire containing source's materials
        // (16 real items reduced to "Материалов пока нет" with no visible
        // error, since the failure got swallowed by a Result.getOrNull()
        // fallback layer upstream).
        server.enqueue(
            jsonResponse(
                """{"data":[{"item_id":"item-1","title":"v0.19.0","link_preview":[]}]}"""
            )
        )

        val items = api.listSourceItems("Bearer test-only", sourceId = "src-1", limit = 20)

        assertEquals(1, items.size)
        assertEquals("item-1", items[0].itemId)
        assertEquals(emptyMap<String, Any?>(), items[0].linkPreview)
    }

    @Test
    fun `listSources request path has no trailing slash`() = runBlocking {
        server.enqueue(jsonResponse("[]"))

        api.listSources("Bearer test-only", scope = "project_soll")
        val request = server.takeRequest()

        assertEquals("/api/v1/soll/sources?scope=project_soll", request.path)
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setBody(body).setHeader("Content-Type", "application/json")
}
