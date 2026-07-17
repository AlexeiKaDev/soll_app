package com.soll.domain.soll

import com.soll.domain.assistant.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SollChatActionPolicyTest {
    @Test
    fun `supported actions resolve to registered capabilities`() {
        val registeredCapabilities = CapabilityRegistry.CURRENT_COMMAND_CAPABILITIES
            .map { it.id }
            .toSet()

        SollChatActionPolicyRegistry.supportedTypes().forEach { type ->
            val policy = requireNotNull(SollChatActionPolicyRegistry.resolve(type))
            assertTrue("Unknown capability for $type", policy.capabilityId in registeredCapabilities)
            assertTrue("Chat action must require an explicit tap: $type", policy.requiresExplicitUserTap)
        }
    }

    @Test
    fun `action types are normalized and unknown actions fail closed`() {
        assertEquals("task.done", SollChatActionPolicyRegistry.resolve(" TASK.DONE ")?.type)
        assertEquals("server_action", SollChatActionPolicyRegistry.resolve("approval.approve")?.capabilityId)
        assertNull(SollChatActionPolicyRegistry.resolve("shell.execute"))
        assertNull(SollChatActionPolicyRegistry.resolve(""))
    }
}
