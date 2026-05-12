package com.soll.domain.device

import org.json.JSONObject

data class GadgetSettingsDraft(
    val deviceName: String = "",
    val timezone: String = "",
    val sensorIntervalMs: Int? = null,
    val displayBrightness: Int? = null,
    val autoMode: Boolean = false,
)

data class GadgetCalibrationDraft(
    val sensorKey: String = "",
    val offset: Double? = null,
    val referenceValue: Double? = null,
)

data class GadgetScheduleDraft(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val time: String = "",
    val action: String = "",
    val enabled: Boolean = true,
)

data class GadgetAutomationDraft(
    val id: String = "",
    val name: String = "",
    val sensorKey: String = "",
    val operator: String = ">",
    val threshold: Double? = null,
    val action: String = "",
    val enabled: Boolean = true,
)

object GadgetEditorPayloads {
    fun settings(draft: GadgetSettingsDraft): String {
        val settings = JSONObject()
            .put("autoMode", draft.autoMode)
        draft.deviceName.trim().takeIf { it.isNotBlank() }?.let { settings.put("deviceName", it) }
        draft.timezone.trim().takeIf { it.isNotBlank() }?.let { settings.put("timezone", it) }
        draft.sensorIntervalMs?.takeIf { it > 0 }?.let { settings.put("sensorInterval", it) }
        draft.displayBrightness?.coerceIn(0, 255)?.let { settings.put("displayBrightness", it) }
        return withTopLevelCopies("settings", settings).toString()
    }

    fun calibration(draft: GadgetCalibrationDraft): String {
        val sensor = draft.sensorKey.trim()
        require(sensor.isNotBlank()) { "Укажите датчик для калибровки" }
        val calibration = JSONObject().put("sensor", sensor)
        draft.offset?.let { calibration.put("offset", it) }
        draft.referenceValue?.let { calibration.put("referenceValue", it) }
        return withTopLevelCopies("calibration", calibration).toString()
    }

    fun schedule(draft: GadgetScheduleDraft): String {
        val schedule = JSONObject()
            .put("name", draft.name.trim().ifBlank { "Расписание" })
            .put("type", draft.type.trim().ifBlank { "custom" })
            .put("time", draft.time.trim())
            .put("action", draft.action.trim())
            .put("enabled", draft.enabled)
        draft.id.trim().takeIf { it.isNotBlank() }?.let { schedule.put("id", it) }
        require(schedule.getString("time").isNotBlank()) { "Укажите время расписания" }
        require(schedule.getString("action").isNotBlank()) { "Укажите действие расписания" }
        return withTopLevelCopies("schedule", schedule).toString()
    }

    fun deleteSchedule(id: String): String {
        val cleanId = id.trim()
        require(cleanId.isNotBlank()) { "Укажите ID расписания" }
        return JSONObject().put("id", cleanId).toString()
    }

    fun automation(draft: GadgetAutomationDraft): String {
        val sensor = draft.sensorKey.trim()
        val action = draft.action.trim()
        require(sensor.isNotBlank()) { "Укажите датчик автоматизации" }
        require(action.isNotBlank()) { "Укажите действие автоматизации" }
        val condition = JSONObject()
            .put("sensor", sensor)
            .put("operator", draft.operator.trim().ifBlank { ">" })
        draft.threshold?.let { condition.put("value", it) }
        val rule = JSONObject()
            .put("name", draft.name.trim().ifBlank { "Автоматизация" })
            .put("enabled", draft.enabled)
            .put("condition", condition)
            .put("action", action)
        draft.id.trim().takeIf { it.isNotBlank() }?.let { rule.put("id", it) }
        return withTopLevelCopies("rule", rule)
            .put("sensor", sensor)
            .put("operator", condition.getString("operator"))
            .apply { draft.threshold?.let { put("threshold", it) } }
            .put("action", action)
            .toString()
    }

    fun deleteAutomation(id: String): String {
        val cleanId = id.trim()
        require(cleanId.isNotBlank()) { "Укажите ID автоматизации" }
        return JSONObject().put("id", cleanId).toString()
    }

    private fun withTopLevelCopies(key: String, nested: JSONObject): JSONObject {
        val payload = JSONObject().put(key, nested)
        nested.keys().forEach { nestedKey ->
            payload.put(nestedKey, nested.get(nestedKey))
        }
        return payload
    }
}
