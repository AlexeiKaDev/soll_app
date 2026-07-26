package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.soll.data.local.entity.TaskGraphEdgeEntity
import com.soll.data.local.entity.TaskGraphNodeEntity
import com.soll.data.local.entity.TaskGraphReachabilityEntity
import com.soll.data.local.entity.TaskGraphSnapshotEntity
import com.soll.domain.soll.SollTaskGraph
import com.soll.domain.soll.SollTaskGraphEdge
import com.soll.domain.soll.SollTaskGraphNode
import java.util.PriorityQueue

@Dao
interface TaskGraphCacheDao {
    @Query("SELECT * FROM task_graph_snapshots WHERE scope = :scope LIMIT 1")
    suspend fun getSnapshot(scope: String): TaskGraphSnapshotEntity?

    @Query("SELECT * FROM task_graph_nodes WHERE scope = :scope ORDER BY id")
    suspend fun getNodes(scope: String): List<TaskGraphNodeEntity>

    @Query("SELECT * FROM task_graph_edges WHERE scope = :scope ORDER BY id")
    suspend fun getEdges(scope: String): List<TaskGraphEdgeEntity>

    @Query(
        """
        SELECT node.*
        FROM task_graph_reachability AS reachability
        INNER JOIN task_graph_nodes AS node
            ON node.scope = reachability.scope
            AND node.id = reachability.descendant_id
        WHERE reachability.scope = :scope
            AND reachability.ancestor_id = :ancestorId
            AND reachability.descendant_id != :ancestorId
            AND (:kind IS NULL OR node.kind = :kind)
        ORDER BY node.id
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getReachableNodes(
        scope: String,
        ancestorId: String,
        kind: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<TaskGraphNodeEntity>

    @Query(
        """
        SELECT path_count
        FROM task_graph_reachability
        WHERE scope = :scope
            AND ancestor_id = :ancestorId
            AND descendant_id = :descendantId
        LIMIT 1
        """,
    )
    suspend fun getPathCount(scope: String, ancestorId: String, descendantId: String): Long?

    @Transaction
    suspend fun readReachableNodes(
        scope: String,
        ancestorId: String,
        kind: String? = null,
        limit: Int = 200,
    ): List<TaskGraphNodeEntity>? {
        if (getSnapshot(scope) == null) return null
        return getReachableNodes(
            scope = scope,
            ancestorId = ancestorId,
            kind = kind,
            limit = limit,
        )
    }

    @Query("DELETE FROM task_graph_snapshots WHERE scope = :scope")
    suspend fun deleteSnapshot(scope: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshot(snapshot: TaskGraphSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNodes(nodes: List<TaskGraphNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEdges(edges: List<TaskGraphEdgeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReachability(rows: List<TaskGraphReachabilityEntity>)

    @Transaction
    suspend fun replaceGraph(
        scope: String,
        includeDone: Boolean,
        graph: SollTaskGraph,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        val reachability = TaskGraphReachabilityBuilder.build(
            scope = scope,
            nodes = graph.nodes,
            edges = graph.edges,
        )
        val nodes = graph.nodes.map { TaskGraphNodeEntity.fromDomain(scope, it) }
        val edges = graph.edges.map { TaskGraphEdgeEntity.fromDomain(scope, it) }

        deleteSnapshot(scope)
        insertSnapshot(
            TaskGraphSnapshotEntity(
                scope = scope,
                includeDone = includeDone,
                totalTasks = graph.totalTasks,
                truncated = graph.truncated,
                updatedAt = updatedAt,
            ),
        )
        insertNodes(nodes)
        insertEdges(edges)
        insertReachability(reachability)
    }

    @Transaction
    suspend fun readGraph(scope: String): SollTaskGraph? {
        val snapshot = getSnapshot(scope) ?: return null
        return SollTaskGraph(
            nodes = getNodes(scope).map { it.toDomain() },
            edges = getEdges(scope).map { it.toDomain() },
            totalTasks = snapshot.totalTasks,
            truncated = snapshot.truncated,
        )
    }
}

internal object TaskGraphReachabilityBuilder {
    fun build(
        scope: String,
        nodes: List<SollTaskGraphNode>,
        edges: List<SollTaskGraphEdge>,
        maxRows: Int = MAX_REACHABILITY_ROWS,
    ): List<TaskGraphReachabilityEntity> {
        require(scope.isNotBlank()) { "Task graph scope must not be blank" }
        require(maxRows > 0) { "Task graph reachability limit must be positive" }
        require(nodes.all { it.id.isNotBlank() }) { "Task graph node IDs must not be blank" }
        require(edges.all { it.id.isNotBlank() }) { "Task graph edge IDs must not be blank" }

        val nodeIds = nodes.map { it.id }
        require(nodeIds.toSet().size == nodeIds.size) { "Task graph node IDs must be unique" }
        require(edges.map { it.id }.toSet().size == edges.size) { "Task graph edge IDs must be unique" }

        val indegree = nodeIds.associateWith { 0 }.toMutableMap()
        val outgoing = nodeIds.associateWith { mutableListOf<String>() }.toMutableMap()
        edges.forEach { edge ->
            require(edge.source in indegree) { "Unknown task graph source node: ${edge.source}" }
            require(edge.target in indegree) { "Unknown task graph target node: ${edge.target}" }
            outgoing.getValue(edge.source).add(edge.target)
            indegree[edge.target] = indegree.getValue(edge.target) + 1
        }

        val ready = PriorityQueue<String>()
        indegree.filterValues { it == 0 }.keys.forEach(ready::add)
        val topologicalOrder = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val nodeId = ready.remove()
            topologicalOrder += nodeId
            outgoing.getValue(nodeId).sorted().forEach { targetId ->
                val remaining = indegree.getValue(targetId) - 1
                indegree[targetId] = remaining
                if (remaining == 0) ready.add(targetId)
            }
        }
        require(topologicalOrder.size == nodeIds.size) {
            "Task graph must be acyclic before building the reachability index"
        }

        val pathsFrom = mutableMapOf<String, LinkedHashMap<String, Long>>()
        var rowCount = 0
        topologicalOrder.asReversed().forEach { ancestorId ->
            val paths = linkedMapOf(ancestorId to 1L)
            outgoing.getValue(ancestorId).sorted().forEach { childId ->
                pathsFrom.getValue(childId).forEach { (descendantId, childPathCount) ->
                    paths[descendantId] = saturatedAdd(paths[descendantId] ?: 0L, childPathCount)
                }
            }
            pathsFrom[ancestorId] = paths
            rowCount += paths.size
            require(rowCount <= maxRows) {
                "Task graph reachability index exceeds $maxRows rows"
            }
        }

        return topologicalOrder.flatMap { ancestorId ->
            pathsFrom.getValue(ancestorId)
                .toSortedMap()
                .map { (descendantId, pathCount) ->
                    TaskGraphReachabilityEntity(
                        scope = scope,
                        ancestorId = ancestorId,
                        descendantId = descendantId,
                        pathCount = pathCount,
                    )
                }
        }
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private const val MAX_REACHABILITY_ROWS = 50_000
}
