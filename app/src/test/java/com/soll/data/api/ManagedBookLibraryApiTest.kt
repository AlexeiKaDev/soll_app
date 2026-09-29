package com.soll.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ManagedBookLibraryApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SollApiService

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build()
            .create(SollApiService::class.java)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `library preserves stable identity and size`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"books":[{"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","title":"Book","author":"Author","language":"ru","has_cover":true,"size_bytes":123,"sha256":"${"a".repeat(64)}"}]}"""
        ))
        val result = api.getManagedBookLibrary("Bearer owner")
        val request = server.takeRequest()
        assertEquals("/api/v1/books/library", request.path)
        assertEquals("Bearer owner", request.getHeader("Authorization"))
        assertEquals(123L, result.books.single().sizeBytes)
        assertTrue(result.books.single().hasCover)
    }

    @Test fun `managed epub download is authenticated binary`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/epub+zip").setBody("epub"))
        val body = api.downloadManagedBook("Bearer owner", "b".repeat(32))
        val request = server.takeRequest()
        assertEquals("/api/v1/books/library/${"b".repeat(32)}/file", request.path)
        assertEquals("Bearer owner", request.getHeader("Authorization"))
        assertEquals("epub", body.string())
    }

    @Test fun `managed epub upload is authenticated multipart and returns stable identity`() = runBlocking {
        val bookId = "9".repeat(32)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"book":{"id":"$bookId","title":"Uploaded","author":"","language":"ru","has_cover":false,"size_bytes":4,"sha256":"${"9".repeat(64)}"},"created":true}"""
        ))
        val file = MultipartBody.Part.createFormData(
            "file",
            "uploaded.epub",
            "epub".toRequestBody("application/epub+zip".toMediaType()),
        )
        val result = api.uploadManagedBook("Bearer owner", file)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/books/library/import", request.path)
        assertEquals("Bearer owner", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"uploaded.epub\""))
        assertTrue(body.contains("epub"))
        assertEquals(bookId, result.book?.id)
        assertTrue(result.created)
    }

    @Test fun `reading position round trip uses authenticated revision contract`() = runBlocking {
        val bookId = "c".repeat(32)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"position":{"book_id":"$bookId","chapter_index":2,"fraction":0.4,"locator":"#p2","updated_at":"2026-09-29T09:00:00Z"}}"""
        ))
        val loaded = api.getManagedBookPosition("Bearer owner", bookId).position!!
        val getRequest = server.takeRequest()
        assertEquals("/api/v1/books/library/$bookId/position", getRequest.path)
        assertEquals("Bearer owner", getRequest.getHeader("Authorization"))
        assertEquals(2, loaded.chapterIndex)

        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"position":{"book_id":"$bookId","chapter_index":3,"fraction":0.75,"locator":"","updated_at":"2026-09-29T09:01:00Z"}}"""
        ))
        val saved = api.saveManagedBookPosition(
            "Bearer owner",
            bookId,
            ManagedBookPositionRequest(
                chapterIndex = 3,
                fraction = 0.75,
                expectedUpdatedAt = loaded.updatedAt,
            ),
        ).position!!
        val putRequest = server.takeRequest()
        val body = putRequest.body.readUtf8()
        assertEquals("PUT", putRequest.method)
        assertEquals("/api/v1/books/library/$bookId/position", putRequest.path)
        assertEquals("Bearer owner", putRequest.getHeader("Authorization"))
        assertTrue(body.contains("\"chapter_index\":3"))
        assertTrue(body.contains("\"expected_updated_at\":\"2026-09-29T09:00:00Z\""))
        assertEquals("2026-09-29T09:01:00Z", saved.updatedAt)
    }

    @Test fun `managed notes use authenticated snake case contract`() = runBlocking {
        val bookId = "d".repeat(32)
        val noteId = "e".repeat(32)
        val noteJson = """{"id":"$noteId","book_id":"$bookId","text":"Idea","chapter_index":1,"selected_text":"","locator":"","created_at":"2026-09-29T09:00:00Z","updated_at":"2026-09-29T09:00:00Z"}"""
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"notes":[$noteJson]}"""
        ))
        val notes = api.getManagedBookNotes("Bearer owner", bookId).notes
        val listRequest = server.takeRequest()
        assertEquals("/api/v1/books/library/$bookId/notes", listRequest.path)
        assertEquals("Bearer owner", listRequest.getHeader("Authorization"))
        assertEquals(noteId, notes.single().id)

        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"note":$noteJson}"""
        ))
        api.createManagedBookNote(
            "Bearer owner", bookId, ManagedBookNoteRequest("Idea", chapterIndex = 1)
        )
        val createRequest = server.takeRequest()
        assertEquals("POST", createRequest.method)
        assertTrue(createRequest.body.readUtf8().contains("\"chapter_index\":1"))

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(api.deleteManagedBookNote("Bearer owner", bookId, noteId).isSuccessful)
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/api/v1/books/library/$bookId/notes/$noteId", deleteRequest.path)
    }

    @Test fun `whole book summary uses authenticated durable contract`() = runBlocking {
        val bookId = "f".repeat(32)
        val response = """{"status":"completed","completed":true,"book_id":"$bookId","book_sha256":"${"a".repeat(64)}","summary":"Whole book","answer_verification":"not_independently_verified","chapter_count":4,"character_count":9000,"segment_count":2,"created_at":"2026-09-29T10:00:00Z","cache":"miss"}"""
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(response))
        val created = api.createManagedBookSummary(
            "Bearer owner", bookId, ManagedBookSummaryRequest(force = true)
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/books/library/$bookId/summary", request.path)
        assertEquals("Bearer owner", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"force\":true"))
        assertEquals("Whole book", created.summary)
        assertEquals(4, created.chapterCount)
    }
}
