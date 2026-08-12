package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HabrYandexOrgGraphSourceTriageTest {
    @Test
    fun `Yandex closure model is applied to the Soll task graph service`() {
        val database = projectFile("app/src/main/java/com/soll/data/local/SollDatabase.kt").readText()
        val entities = projectFile(
            "app/src/main/java/com/soll/data/local/entity/TaskGraphEntities.kt",
        ).readText()
        val dao = projectFile(
            "app/src/main/java/com/soll/data/local/dao/TaskGraphCacheDao.kt",
        ).readText()
        val repository = projectFile(
            "app/src/main/java/com/soll/data/repository/SollRepository.kt",
        ).readText()
        val appModule = projectFile("app/src/main/java/com/soll/di/AppModule.kt").readText()
        val viewModel = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardViewModel.kt",
        ).readText()
        val screen = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardScreen.kt",
        ).readText()
        val migrationTest = projectFile(
            "app/src/androidTest/java/com/soll/data/local/TaskGraphMigrationTest.kt",
        ).readText()
        val schema = projectFile("app/schemas/com.soll.data.local.SollDatabase/24.json").readText()
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val knowledge = projectFile("docs/knowledge/task-graph-reachability-index.md").readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-0d75242b770a-97f971eb6ef1eec9-verification.md",
        ).readText()

        listOf(
            "version = 25",
            "TaskGraphSnapshotEntity::class",
            "TaskGraphNodeEntity::class",
            "TaskGraphEdgeEntity::class",
            "TaskGraphReachabilityEntity::class",
            "abstract fun taskGraphCacheDao()",
        ).forEach { contract ->
            assertTrue("Missing task graph database contract: $contract", database.contains(contract))
        }

        listOf(
            "tableName = \"task_graph_snapshots\"",
            "tableName = \"task_graph_nodes\"",
            "tableName = \"task_graph_edges\"",
            "tableName = \"task_graph_reachability\"",
            "primaryKeys = [\"scope\", \"ancestor_id\", \"descendant_id\"]",
            "Index(value = [\"scope\", \"descendant_id\", \"ancestor_id\"])",
            "val pathCount: Long",
        ).forEach { contract ->
            assertTrue("Missing reference-counted schema contract: $contract", entities.contains(contract))
        }

        listOf(
            "INNER JOIN task_graph_nodes AS node",
            "reachability.ancestor_id = :ancestorId",
            "ORDER BY node.id",
            "LIMIT :limit OFFSET :offset",
            "suspend fun getPathCount",
            "suspend fun readReachableNodes",
            "Task graph must be acyclic",
            "Task graph reachability index exceeds",
            "suspend fun replaceGraph",
            "suspend fun readGraph",
        ).forEach { query ->
            assertTrue("Missing task graph cache/query behavior: $query", dao.contains(query))
        }

        listOf(
            "migration23To24",
            "CREATE TABLE IF NOT EXISTS `task_graph_reachability`",
            "index_task_graph_reachability_scope_descendant_id_ancestor_id",
        ).forEach { migration ->
            assertTrue("Missing task graph migration: $migration", appModule.contains(migration))
        }

        listOf(
            "\"version\": 24",
            "\"tableName\": \"task_graph_snapshots\"",
            "\"tableName\": \"task_graph_nodes\"",
            "\"tableName\": \"task_graph_edges\"",
            "\"tableName\": \"task_graph_reachability\"",
            "\"columnName\": \"path_count\"",
        ).forEach { exportedSchema ->
            assertTrue("Missing exported Room schema evidence: $exportedSchema", schema.contains(exportedSchema))
        }

        listOf(
            "cacheTaskGraphBestEffort(graph, includeDone)",
            "cachedTaskGraphOrNull(includeDone)",
            "getTaskGraphDescendants(",
            "readReachableNodes(",
            "TASK_GRAPH_SCOPE_OPEN",
            "TASK_GRAPH_SCOPE_ALL",
        ).forEach { behavior ->
            assertTrue("Missing SollRepository graph fallback: $behavior", repository.contains(behavior))
        }

        listOf(
            "sollGateway.getTaskGraph(includeDone = false)",
            "sollGateway.getTaskGraphDescendants(",
            ".filterByGraphSelection(this)",
            "projectFilterNodes()",
        ).forEach { behavior ->
            assertTrue("Missing production graph filter behavior: $behavior", viewModel.contains(behavior))
        }
        listOf(
            "graphRoots = uiState.taskGraph?.projectFilterNodes().orEmpty()",
            "onGraphNodeChange = viewModel::selectGraphNode",
            "text = \"Проект\"",
        ).forEach { behavior ->
            assertTrue("Missing project graph filter UI: $behavior", screen.contains(behavior))
        }
        listOf(
            "runMigrationsAndValidate(",
            "AppModule.migration23To24",
            "dao.replaceGraph(",
            "dao.getReachableNodes(",
            "dao.getPathCount(",
        ).forEach { behavior ->
            assertTrue("Missing Room migration/DAO smoke behavior: $behavior", migrationTest.contains(behavior))
        }

        listOf(
            "source-item/0d75242b770a/97f971eb6ef1eec9",
            "docs/knowledge/task-graph-reachability-index.md",
            "bridge + reference-counted closure",
            "schema 24",
        ).forEach { decision ->
            assertTrue("Missing task-graph roadmap decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "https://habr.com/ru/companies/yandex/articles/1046483/",
            "`task_graph_edges`",
            "`task_graph_reachability`",
            "`path_count`",
            "Two independent scopes, `open` and `all`",
            "getReachableNodes",
            "device storage impact have not yet been measured",
        ).forEach { evidence ->
            assertTrue("Missing adapted graph-index evidence: $evidence", knowledge.contains(evidence))
        }

        listOf(
            "source_processing_result: implemented_task_graph_reachability_cache",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-0d75242b770a-97f971eb6ef1eec9-verification.md",
            "4 Room tables, 5 secondary indexes, 2 cache scopes",
            "one `A/D` closure row with `path_count = 3`",
            "p50/p95 latency",
            "TaskGraphReachabilityBuilderTest",
        ).forEach { evidence ->
            assertTrue("Missing source-processing value evidence: $evidence", verification.contains(evidence))
        }
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }
}
