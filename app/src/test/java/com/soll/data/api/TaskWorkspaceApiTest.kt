package com.soll.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class TaskWorkspaceApiTest {
    @Test fun `roadmap edits wait for upgraded server and archive metadata is retained`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val service = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .build().create(SollApiService::class.java)
            server.enqueue(MockResponse().setBody("""{"current_stage":"current","stages":[]}"""))
            assertFalse(service.getRoadmap("Bearer test-device").editSupported)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"edit_supported":true,"stages":[{"id":"current","lines":[{"line":"history","text":"Сохранённый пункт","status":"declined","comment":"В истории"}]}]}"""))
            val upgraded = service.getRoadmap("Bearer test-device")
            assertTrue(upgraded.editSupported)
            assertEquals("declined", upgraded.stages.single().lines.single().status)
            assertEquals("В истории", upgraded.stages.single().lines.single().comment)
        } finally { server.shutdown() }
    }

    @Test fun `creation and lookup accept both core and relay task responses`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val service = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .build().create(SollApiService::class.java)
            server.enqueue(MockResponse().setBody("""{"id":"task-one","title":"Работа","status":"inbox"}"""))
            val created = service.createTask("Bearer test-device", TaskCreateRequest("Работа", "Описание", "A", "stable-request"))
            assertEquals("task-one", created.taskResponse().id)
            val request = server.takeRequest()
            assertEquals("/api/v1/tasks/", request.path)
            assertEquals("Bearer test-device", request.getHeader("Authorization"))
            assertEquals("stable-request", JSONObject(request.body.readUtf8()).getString("client_request_id"))
            server.enqueue(MockResponse().setBody("""{"success":true,"task":{"id":"task-one","title":"Работа","status":"inbox"}}"""))
            assertEquals("task-one", service.getTask("Bearer test-device", "task-one").taskResponse().id)
            assertEquals("/api/v1/tasks/task-one", server.takeRequest().path)
        } finally { server.shutdown() }
    }

    @Test fun `page uses server filters and editor sends explicit clears`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val service = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .build().create(SollApiService::class.java)
            server.enqueue(MockResponse().setBody("""{"tasks":[{"id":"late","title":"Позвонить"}],"total_count":501,"has_more":true}"""))
            val page = service.getTaskPage("Bearer test-device", "done", "Позвонить", "project-one", "A", 480)
            assertTrue(page.hasMore)
            assertEquals(501, page.totalCount)
            val url = requireNotNull(server.takeRequest().requestUrl)
            assertEquals("480", url.queryParameter("offset"))
            assertEquals("Позвонить", url.queryParameter("q"))
            assertEquals("done", url.queryParameter("section"))
            server.enqueue(MockResponse().setBody("""{"task":{"id":"late","title":"Позвонить"}}"""))
            service.updateTask("Bearer test-device", "late", TaskUpdateRequest("Позвонить", "Описание", "C", "", "", ""))
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("", body.getString("due_date"))
            assertEquals("", body.getString("project_id"))
            assertEquals("C", body.getString("priority"))
        } finally { server.shutdown() }
    }
}
