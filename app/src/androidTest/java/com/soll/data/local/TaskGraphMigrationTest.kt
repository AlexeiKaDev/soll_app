package com.soll.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soll.di.AppModule
import com.soll.domain.soll.SollTaskGraph
import com.soll.domain.soll.SollTaskGraphEdge
import com.soll.domain.soll.SollTaskGraphNode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskGraphMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation,
        SollDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun clearDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate23To24AndQueryReachabilityThroughDao() {
        migrationHelper.createDatabase(DATABASE_NAME, 23).close()
        migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            24,
            true,
            AppModule.migration23To24,
        ).close()

        val database = Room.databaseBuilder(context, SollDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppModule.migration23To24)
            .build()
        try {
            runBlocking {
                val graph = SollTaskGraph(
                    nodes = listOf("A", "B", "C", "D").map { id ->
                        SollTaskGraphNode(id = id, kind = "task", label = id, taskId = id)
                    },
                    edges = listOf(
                        edge("A-B", "A", "B"),
                        edge("A-C", "A", "C"),
                        edge("B-D", "B", "D"),
                        edge("C-D", "C", "D"),
                        edge("A-D", "A", "D"),
                    ),
                    totalTasks = 4,
                )
                val dao = database.taskGraphCacheDao()

                dao.replaceGraph(scope = "open", includeDone = false, graph = graph)

                val cached = requireNotNull(dao.readGraph("open"))
                assertEquals(graph.nodes.sortedBy { it.id }, cached.nodes)
                assertEquals(graph.edges.sortedBy { it.id }, cached.edges)
                assertEquals(graph.totalTasks, cached.totalTasks)
                assertEquals(listOf("B", "C", "D"), dao.getReachableNodes("open", "A").map { it.id })
                assertEquals(3L, dao.getPathCount("open", "A", "D"))
            }
        } finally {
            database.close()
        }
    }

    private fun edge(id: String, source: String, target: String): SollTaskGraphEdge =
        SollTaskGraphEdge(id = id, source = source, target = target, kind = "contains")

    private companion object {
        const val DATABASE_NAME = "task-graph-migration-test"
    }
}
