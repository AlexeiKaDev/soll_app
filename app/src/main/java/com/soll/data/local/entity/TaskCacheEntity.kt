package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.soll.SollTask
import org.json.JSONArray

@Entity(
    tableName = "task_cache",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
    ],
)
data class TaskCacheEntity(
    @PrimaryKey
    val id: String,

    val title: String,
    val description: String,

    @ColumnInfo(name = "source_ref")
    val sourceRef: String,

    @ColumnInfo(name = "project_name")
    val projectName: String?,

    val status: String,
    val priority: String,

    @ColumnInfo(name = "due_date")
    val dueDate: String?,

    @ColumnInfo(name = "tags_json")
    val tagsJson: String,

    @ColumnInfo(name = "approval_id")
    val approvalId: String?,

    @ColumnInfo(name = "tool_job_id")
    val toolJobId: String?,

    @ColumnInfo(name = "execution_state")
    val executionState: String,

    @ColumnInfo(name = "outcome_artifacts_json")
    val outcomeArtifactsJson: String,

    @ColumnInfo(name = "value_metric")
    val valueMetric: String,

    val branch: String,

    @ColumnInfo(name = "pair_id")
    val pairId: String?,

    @ColumnInfo(name = "assigned_node_id")
    val assignedNodeId: String?,

    @ColumnInfo(name = "required_capabilities_json")
    val requiredCapabilitiesJson: String,

    @ColumnInfo(name = "routing_state")
    val routingState: String,

    @ColumnInfo(name = "execution_phase")
    val executionPhase: String,

    @ColumnInfo(name = "execution_reason")
    val executionReason: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    fun toDomain(): SollTask =
        SollTask(
            id = id,
            title = title,
            description = description,
            sourceRef = sourceRef,
            projectName = projectName,
            status = status,
            priority = priority,
            dueDate = dueDate,
            tags = JSONArray(tagsJson).toStringList(),
            approvalId = approvalId,
            toolJobId = toolJobId,
            executionState = executionState,
            outcomeArtifacts = JSONArray(outcomeArtifactsJson).toStringList(),
            valueMetric = valueMetric,
            branch = branch,
            pairId = pairId,
            assignedNodeId = assignedNodeId,
            requiredCapabilities = JSONArray(requiredCapabilitiesJson).toStringList(),
            routingState = routingState,
            executionPhase = executionPhase,
            executionReason = executionReason,
        )

    companion object {
        fun fromDomain(task: SollTask, updatedAt: Long): TaskCacheEntity =
            TaskCacheEntity(
                id = task.id,
                title = task.title,
                description = task.description,
                sourceRef = task.sourceRef,
                projectName = task.projectName,
                status = task.status,
                priority = task.priority,
                dueDate = task.dueDate,
                tagsJson = JSONArray(task.tags).toString(),
                approvalId = task.approvalId,
                toolJobId = task.toolJobId,
                executionState = task.executionState,
                outcomeArtifactsJson = JSONArray(task.outcomeArtifacts).toString(),
                valueMetric = task.valueMetric,
                branch = task.branch,
                pairId = task.pairId,
                assignedNodeId = task.assignedNodeId,
                requiredCapabilitiesJson = JSONArray(task.requiredCapabilities).toString(),
                routingState = task.routingState,
                executionPhase = task.executionPhase,
                executionReason = task.executionReason,
                updatedAt = updatedAt,
            )
    }
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
