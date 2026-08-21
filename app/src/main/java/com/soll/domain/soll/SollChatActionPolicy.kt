package com.soll.domain.soll

data class SollChatActionPolicy(
    val type: String,
    val capabilityId: String,
    val requiresExplicitUserTap: Boolean,
)

object SollChatActionPolicyRegistry {
    private val policies = listOf(
        policy("task.complete", "tasks"),
        policy("task.done", "tasks"),
        policy("task.defer", "tasks"),
        policy("task.reject", "tasks"),
        policy("task.today", "tasks"),
        policy("task.start", "tasks"),
        policy("task.block", "tasks"),
        policy("task.stale", "tasks"),
        policy("task.inbox", "tasks"),
        policy("task.update", "tasks"),
        policy("task.clarify", "tasks"),
        policy("notice.ack", "chat"),
        policy("approval.approve", "server_action"),
        policy("approval.reject", "server_action"),
        policy("review.approve", "server_action"),
        policy("review.reject", "server_action"),
        policy("review.recheck", "server_action"),
        policy("task.open", "tasks"),
        policy("web_ingest.approve", "server_action"),
        policy("web_ingest.reject", "server_action"),
    )
    private val byType = policies.associateBy(SollChatActionPolicy::type)

    fun resolve(type: String): SollChatActionPolicy? =
        byType[type.trim().lowercase()]

    fun supportedTypes(): Set<String> = byType.keys

    private fun policy(type: String, capabilityId: String) = SollChatActionPolicy(
        type = type,
        capabilityId = capabilityId,
        requiresExplicitUserTap = true,
    )
}
