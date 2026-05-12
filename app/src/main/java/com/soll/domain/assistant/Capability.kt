package com.soll.domain.assistant

data class Capability(
    val id: String,
    val name: String,
    val description: String,
    val riskTier: RiskTier,
    val requiredAndroidPermissions: List<String>,
    val requiresConfirmation: Boolean,
    val enabledByDefault: Boolean,
    val auditRequired: Boolean,
)

enum class RiskTier {
    SAFE_INFO,
    PERSONAL_DATA,
    DEVICE_CONTROL,
    COMMUNICATION,
    FILE_MEDIA,
    MONEY_OR_EXTERNAL_ACTION,
    DUAL_USE_HARDWARE,
    BLOCKED,
}

enum class CapabilityBlockReason {
    NOT_REGISTERED,
    BLOCKED_TIER,
    RISKY_CAPABILITIES_DISABLED,
    CAPABILITY_DISABLED,
}

data class CapabilityDecision(
    val allowed: Boolean,
    val capability: Capability?,
    val reason: CapabilityBlockReason? = null,
    val message: String = "",
)

interface CapabilitySettings {
    fun isRiskyCapabilitiesEnabled(): Boolean
    fun isCapabilityEnabled(capability: Capability): Boolean
}
