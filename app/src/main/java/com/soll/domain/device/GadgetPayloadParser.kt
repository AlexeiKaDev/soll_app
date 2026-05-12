package com.soll.domain.device

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object GadgetPayloadParser {
    fun telemetry(
        response: DeviceCommandResponse,
        deviceId: String,
    ): DeviceTelemetry {
        val root = response.dataJson.parseObjectOrEmpty()
        val sensors = root.optJSONObject("sensors") ?: root
        return DeviceTelemetry(
            deviceId = deviceId,
            values = sensors.toTelemetryValues(),
            rawJson = response.rawJson,
            timestamp = response.timestamp ?: System.currentTimeMillis(),
        )
    }

    fun telemetry(
        deviceId: String,
        values: Map<String, Any?>,
        rawJson: String,
        timestamp: Long = System.currentTimeMillis(),
    ): DeviceTelemetry =
        DeviceTelemetry(
            deviceId = deviceId,
            values = values.toTelemetryValues(),
            rawJson = rawJson,
            timestamp = timestamp,
        )

    fun telemetry(snapshot: GadgetCloudSnapshot): DeviceTelemetry? =
        snapshot.latestTelemetry.takeIf { it.isNotEmpty() }?.let { values ->
            telemetry(
                deviceId = snapshot.id,
                values = values,
                rawJson = JSONObject(values).toString(),
            )
        }

    fun actuators(response: DeviceCommandResponse): DeviceActuatorSnapshot {
        val root = response.dataJson.parseObjectOrEmpty()
        val actuators = root.optJSONObject("actuators") ?: root
        val lightValue = actuators.optIntOrNull("lightBrightness")
            ?: actuators.optIntOrNull("brightness")
            ?: actuators.optBooleanOrNull("light")?.let { if (it) 255 else 0 }
        return DeviceActuatorSnapshot(
            airPump = actuators.optBooleanOrNull("airPump")
                ?: actuators.optBooleanOrNull("air_pump"),
            waterPump = actuators.optBooleanOrNull("waterPump")
                ?: actuators.optBooleanOrNull("water_pump")
                ?: actuators.optBooleanOrNull("pump"),
            fan = actuators.optBooleanOrNull("fan"),
            fullLed = actuators.optIntOrNull("fullLed")
                ?: actuators.optIntOrNull("fullLED")
                ?: actuators.optIntOrNull("full")
                ?: lightValue,
            whiteLed = actuators.optIntOrNull("whiteLed")
                ?: actuators.optIntOrNull("whiteLED")
                ?: actuators.optIntOrNull("white"),
        )
    }

    fun config(response: DeviceCommandResponse): GadgetConfigSummary {
        val root = response.dataJson.parseObjectOrEmpty()
        val config = root.optJSONObject("config")
            ?: root.optJSONObject("settings")
            ?: root.optJSONObject("info")
            ?: root
        val items = CONFIG_KEYS.mapNotNull { key ->
            if (!config.has(key) || config.isNull(key)) {
                null
            } else {
                GadgetKeyValue(
                    label = key.configLabel(),
                    value = config.opt(key).formatConfigValue(key),
                )
            }
        }
        return GadgetConfigSummary(
            items = items.ifEmpty { config.toKeyValues(limit = 8) },
            rawJson = response.dataJson.prettyJson(),
        )
    }

    fun schedules(response: DeviceCommandResponse): GadgetScheduleSummary {
        val payload = response.dataJson.parseJsonOrNull()
        val array = when (payload) {
            is JSONArray -> payload
            is JSONObject -> payload.optJSONArray("schedules")
                ?: payload.optJSONArray("schedule")
                ?: JSONArray()
            else -> JSONArray()
        }
        val items = (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { schedule ->
                GadgetScheduleItem(
                    id = schedule.optString("id", (index + 1).toString()),
                    name = schedule.optString("name").ifBlank { "Расписание ${index + 1}" },
                    enabled = schedule.optBoolean("enabled", true),
                    type = schedule.optString("type").ifBlank { "сценарий" },
                    time = schedule.optString("time")
                        .ifBlank { schedule.optString("start") }
                        .ifBlank { schedule.optString("startTime") }
                        .ifBlank { "время не задано" },
                    action = schedule.optString("action")
                        .ifBlank { schedule.optString("state") }
                        .ifBlank { schedule.optString("value") }
                        .ifBlank { "действие не задано" },
                )
            }
        }
        return GadgetScheduleSummary(
            items = items,
            rawJson = response.dataJson.prettyJson(),
        )
    }

    fun diagnostics(response: DeviceCommandResponse): GadgetDiagnosticSummary {
        val root = response.dataJson.parseObjectOrEmpty()
        val devices = root.optJSONArray("devices")
        val items = if (devices != null) {
            (0 until devices.length()).mapNotNull { index ->
                devices.optJSONObject(index)?.let { device ->
                    val address = device.optString("addressHex")
                        .ifBlank { device.optString("address") }
                    GadgetKeyValue(
                        label = "I2C $address",
                        value = device.optString("name").ifBlank { "модуль ${index + 1}" },
                    )
                }
            } + listOfNotNull(root.optIntOrNull("count")?.let { GadgetKeyValue("Найдено", it.toString()) })
        } else {
            root.toKeyValues(limit = 10)
        }
        return GadgetDiagnosticSummary(
            items = items,
            rawJson = response.dataJson.prettyJson(),
        )
    }

    fun prettyJson(text: String): String = text.prettyJson()

    private val CONFIG_KEYS = listOf(
        "deviceName",
        "platform",
        "firmware",
        "firmwareVersion",
        "hardware",
        "chipId",
        "localIP",
        "ip",
        "mac",
        "wifiSSID",
        "wifiRSSI",
        "freeHeap",
        "uptime",
        "timezone",
        "tempUnit",
        "displayBrightness",
        "autoMode",
        "updateInterval",
        "sensorInterval",
        "broadcastInterval",
        "mqttTopic",
        "ntpServer",
    )
}

private fun JSONObject.toTelemetryValues(): List<DeviceSensorValue> =
    keys().asSequence()
        .associateWith { key -> opt(key) }
        .toTelemetryValues()

private fun Map<String, Any?>.toTelemetryValues(): List<DeviceSensorValue> =
    asSequence()
        .filterNot { (key, _) -> key == "timestamp" }
        .map { (key, value) ->
            val definition = GadgetSensorCatalog.definitionFor(key)
            DeviceSensorValue(
                key = key,
                label = GadgetSensorCatalog.labelFor(key),
                value = value.formatSensorValue(definition),
                status = GadgetSensorCatalog.statusFor(key, value.numericValueOrNull()),
            )
        }
        .toList()

private fun JSONObject.toKeyValues(limit: Int): List<GadgetKeyValue> =
    keys().asSequence()
        .filterNot { it.isSensitiveKey() }
        .take(limit)
        .map { key ->
            GadgetKeyValue(
                label = key.configLabel(),
                value = opt(key).formatConfigValue(key),
            )
        }
        .toList()

private fun String.configLabel(): String = when (this) {
    "deviceName" -> "Имя"
    "platform" -> "Платформа"
    "firmware", "firmwareVersion" -> "Прошивка"
    "hardware" -> "Железо"
    "chipId" -> "Chip ID"
    "localIP", "ip" -> "IP"
    "mac" -> "MAC"
    "wifiSSID" -> "Wi-Fi"
    "wifiRSSI" -> "RSSI"
    "freeHeap" -> "Свободная память"
    "uptime" -> "Аптайм"
    "timezone" -> "Часовой пояс"
    "tempUnit" -> "Температура"
    "displayBrightness" -> "Яркость дисплея"
    "autoMode" -> "Авто-режим"
    "updateInterval" -> "Интервал обновления"
    "sensorInterval" -> "Интервал датчиков"
    "broadcastInterval" -> "Интервал рассылки"
    "mqttTopic" -> "MQTT topic"
    "ntpServer" -> "NTP"
    else -> this
}

private fun Any?.formatSensorValue(definition: GadgetSensorDefinition?): String =
    when (this) {
        is JSONObject -> {
            val value = opt("value").formatSensorValue(null)
            val unit = optString("unit").takeIf { it.isNotBlank() } ?: definition?.unit
            listOfNotNull(value, unit).joinToString(" ")
        }
        is Number -> {
            val value = formatNumber(toDouble())
            listOfNotNull(value, definition?.unit).joinToString(" ")
        }
        is Boolean -> if (this) "вкл" else "выкл"
        null, JSONObject.NULL -> "нет данных"
        else -> toString()
    }

private fun Any?.formatConfigValue(key: String): String =
    when {
        key.isSensitiveKey() -> "скрыто"
        this is JSONObject -> toString().prettyJson()
        this is JSONArray -> toString()
        this is Number -> formatNumber(toDouble())
        this is Boolean -> if (this) "вкл" else "выкл"
        this == null || this == JSONObject.NULL -> "нет данных"
        else -> toString()
    }

private fun Any?.numericValueOrNull(): Double? =
    when (this) {
        is Number -> toDouble()
        is JSONObject -> opt("value").numericValueOrNull()
        else -> toString().replace(',', '.').toDoubleOrNull()
    }

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name).coerceIn(0, 255) else null

private fun String.parseJsonOrNull(): Any? =
    runCatching {
        val trimmed = trim()
        when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> null
        }
    }.getOrNull()

private fun String.parseObjectOrEmpty(): JSONObject =
    parseJsonOrNull() as? JSONObject ?: JSONObject()

private fun String.prettyJson(): String =
    runCatching {
        when (val value = parseJsonOrNull()) {
            is JSONObject -> value.toString(2)
            is JSONArray -> value.toString(2)
            else -> this
        }
    }.getOrDefault(this)

private fun String.isSensitiveKey(): Boolean {
    val value = lowercase()
    return value.contains("password") ||
        value.contains("token") ||
        value.contains("secret") ||
        value.contains("apikey") ||
        value.contains("api_key")
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
