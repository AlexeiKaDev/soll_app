package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.soll.domain.soll.SollTaskGraphEdge
import com.soll.domain.soll.SollTaskGraphNode

@Entity(tableName = "task_graph_snapshots", primaryKeys = ["scope"])
data class TaskGraphSnapshotEntity(
    val scope: String,

    @ColumnInfo(name = "include_done")
    val includeDone: Boolean,

    @ColumnInfo(name = "total_tasks")
    val totalTasks: Int,

    val truncated: Boolean,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "task_graph_nodes",
    primaryKeys = ["scope", "id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskGraphSnapshotEntity::class,
            parentColumns = ["scope"],
            childColumns = ["scope"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["scope", "kind", "id"]),
        Index(value = ["scope", "task_id"]),
    ],
)
data class TaskGraphNodeEntity(
    val scope: String,
    val id: String,
    val kind: String,
    val label: String,
    val status: String,
    val priority: String,

    @ColumnInfo(name = "project_id")
    val projectId: String?,

    @ColumnInfo(name = "task_id")
    val taskId: String?,

    @ColumnInfo(name = "source_ref")
    val sourceRef: String,

    val count: Int,
) {
    fun toDomain(): SollTaskGraphNode =
        SollTaskGraphNode(
            id = id,
            kind = kind,
            label = label,
            status = status,
            priority = priority,
            projectId = projectId,
            taskId = taskId,
            sourceRef = sourceRef,
            count = count,
        )

    companion object {
        fun fromDomain(scope: String, node: SollTaskGraphNode): TaskGraphNodeEntity =
            TaskGraphNodeEntity(
                scope = scope,
                id = node.id,
                kind = node.kind,
                label = node.label,
                status = node.status,
                priority = node.priority,
                projectId = node.projectId,
                taskId = node.taskId,
                sourceRef = node.sourceRef,
                count = node.count,
            )
    }
}

@Entity(
    tableName = "task_graph_edges",
    primaryKeys = ["scope", "id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskGraphNodeEntity::class,
            parentColumns = ["scope", "id"],
            childColumns = ["scope", "source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TaskGraphNodeEntity::class,
            parentColumns = ["scope", "id"],
            childColumns = ["scope", "target_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["scope", "source_id", "target_id"]),
        Index(value = ["scope", "target_id", "source_id"]),
    ],
)
data class TaskGraphEdgeEntity(
    val scope: String,
    val id: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "target_id")
    val targetId: String,

    val kind: String,
    val label: String,
) {
    fun toDomain(): SollTaskGraphEdge =
        SollTaskGraphEdge(
            id = id,
            source = sourceId,
            target = targetId,
            kind = kind,
            label = label,
        )

    companion object {
        fun fromDomain(scope: String, edge: SollTaskGraphEdge): TaskGraphEdgeEntity =
            TaskGraphEdgeEntity(
                scope = scope,
                id = edge.id,
                sourceId = edge.source,
                targetId = edge.target,
                kind = edge.kind,
                label = edge.label,
            )
    }
}

@Entity(
    tableName = "task_graph_reachability",
    primaryKeys = ["scope", "ancestor_id", "descendant_id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskGraphNodeEntity::class,
            parentColumns = ["scope", "id"],
            childColumns = ["scope", "ancestor_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TaskGraphNodeEntity::class,
            parentColumns = ["scope", "id"],
            childColumns = ["scope", "descendant_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["scope", "descendant_id", "ancestor_id"]),
    ],
)
data class TaskGraphReachabilityEntity(
    val scope: String,

    @ColumnInfo(name = "ancestor_id")
    val ancestorId: String,

    @ColumnInfo(name = "descendant_id")
    val descendantId: String,

    @ColumnInfo(name = "path_count")
    val pathCount: Long,
)
