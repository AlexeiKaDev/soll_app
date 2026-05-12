package com.soll.domain.command

import com.soll.domain.assistant.Capability
import com.soll.domain.assistant.CapabilityDecision
import com.soll.domain.assistant.CapabilityRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandSafetyGate @Inject constructor(
    private val capabilityRegistry: CapabilityRegistry,
    private val permissionChecker: CapabilityPermissionChecker = AllowAllCapabilityPermissionChecker,
) {
    fun evaluate(command: String, args: String?): CommandSafetyDecision {
        val normalizedCommand = command.lowercase()
        val capabilityDecision = capabilityRegistry.checkCommand(normalizedCommand)
        if (!capabilityDecision.allowed) {
            return CommandSafetyDecision.blocked(
                capabilityDecision = capabilityDecision,
                reason = CommandSafetyBlockReason.CAPABILITY_POLICY,
                message = capabilityDecision.message,
                args = args,
            )
        }

        val capability = requireNotNull(capabilityDecision.capability)
        val parsedArgs = CommandConfirmationParser.parse(args)
        val missingPermissions = permissionChecker.missingPermissions(capability)
        if (missingPermissions.isNotEmpty()) {
            return CommandSafetyDecision.blocked(
                capabilityDecision = capabilityDecision,
                reason = CommandSafetyBlockReason.MISSING_PERMISSION,
                message = missingPermissionsMessage(capability, missingPermissions),
                args = parsedArgs.args,
                missingPermissions = missingPermissions,
            )
        }

        if (capability.requiresConfirmation && !parsedArgs.confirmed) {
            return CommandSafetyDecision.blocked(
                capabilityDecision = capabilityDecision,
                reason = CommandSafetyBlockReason.CONFIRMATION_REQUIRED,
                message = confirmationMessage(command, capability),
                args = parsedArgs.args,
            )
        }

        return CommandSafetyDecision(
            allowed = true,
            capabilityDecision = capabilityDecision,
            reason = null,
            message = "Разрешено",
            normalizedArgs = parsedArgs.args,
        )
    }

    private fun missingPermissionsMessage(
        capability: Capability,
        missingPermissions: List<MissingCapabilityPermission>,
    ): String {
        val labels = missingPermissions.joinToString(", ") { it.label }
        val special = missingPermissions.any { it.specialAccess }
        val place = if (special) {
            "в системных настройках Android и настройках приложения"
        } else {
            "в настройках приложения"
        }
        return "Для «${capability.name}» нет разрешений: $labels. Выдайте их $place."
    }

    private fun confirmationMessage(command: String, capability: Capability): String =
        "Команда /$command относится к рискованным действиям: ${capability.name}. " +
            "Повторите команду с --confirm в конце, если точно хотите выполнить ее."
}

enum class CommandSafetyBlockReason {
    CAPABILITY_POLICY,
    MISSING_PERMISSION,
    CONFIRMATION_REQUIRED,
}

data class CommandSafetyDecision(
    val allowed: Boolean,
    val capabilityDecision: CapabilityDecision,
    val reason: CommandSafetyBlockReason?,
    val message: String,
    val normalizedArgs: String?,
    val missingPermissions: List<MissingCapabilityPermission> = emptyList(),
) {
    val capability: Capability?
        get() = capabilityDecision.capability

    companion object {
        fun blocked(
            capabilityDecision: CapabilityDecision,
            reason: CommandSafetyBlockReason,
            message: String,
            args: String?,
            missingPermissions: List<MissingCapabilityPermission> = emptyList(),
        ): CommandSafetyDecision = CommandSafetyDecision(
            allowed = false,
            capabilityDecision = capabilityDecision,
            reason = reason,
            message = message,
            normalizedArgs = args,
            missingPermissions = missingPermissions,
        )
    }
}
