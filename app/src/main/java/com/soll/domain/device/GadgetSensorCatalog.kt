package com.soll.domain.device

data class GadgetSensorDefinition(
    val keys: Set<String>,
    val label: String,
    val unit: String? = null,
    val warningLow: Double? = null,
    val warningHigh: Double? = null,
    val criticalLow: Double? = null,
    val criticalHigh: Double? = null,
) {
    fun statusFor(value: Double?): DeviceSensorStatus {
        if (value == null) return DeviceSensorStatus.UNKNOWN
        if (criticalLow != null && value < criticalLow) return DeviceSensorStatus.CRITICAL
        if (criticalHigh != null && value > criticalHigh) return DeviceSensorStatus.CRITICAL
        if (warningLow != null && value < warningLow) return DeviceSensorStatus.WARNING
        if (warningHigh != null && value > warningHigh) return DeviceSensorStatus.WARNING
        return if (hasThresholds()) DeviceSensorStatus.NORMAL else DeviceSensorStatus.UNKNOWN
    }

    private fun hasThresholds(): Boolean =
        warningLow != null || warningHigh != null || criticalLow != null || criticalHigh != null
}

object GadgetSensorCatalog {
    private val definitions = listOf(
        GadgetSensorDefinition(
            keys = setOf("waterTemp", "temp_water", "water_temperature"),
            label = "Темп. воды",
            unit = "°C",
            warningLow = 20.0,
            warningHigh = 29.0,
            criticalLow = 16.0,
            criticalHigh = 34.0,
        ),
        GadgetSensorDefinition(
            keys = setOf("airTemp", "temp_air", "bmeTemp", "air_temperature"),
            label = "Темп. воздуха",
            unit = "°C",
            warningLow = 14.0,
            warningHigh = 34.0,
            criticalLow = 5.0,
            criticalHigh = 42.0,
        ),
        GadgetSensorDefinition(
            keys = setOf("humidity", "bmeHumidity"),
            label = "Влажность",
            unit = "%",
            warningLow = 30.0,
            warningHigh = 85.0,
            criticalLow = 15.0,
            criticalHigh = 95.0,
        ),
        GadgetSensorDefinition(
            keys = setOf("pressure", "bmePressure"),
            label = "Давление",
            unit = "гПа",
        ),
        GadgetSensorDefinition(
            keys = setOf("waterLevel", "water_level"),
            label = "Уровень воды",
            warningLow = 20.0,
            criticalLow = 10.0,
        ),
        GadgetSensorDefinition(
            keys = setOf("lightLevel", "light", "lux"),
            label = "Свет",
            unit = "лк",
        ),
        GadgetSensorDefinition(
            keys = setOf("tds"),
            label = "TDS",
            unit = "ppm",
            warningLow = 50.0,
            warningHigh = 700.0,
            criticalLow = 20.0,
            criticalHigh = 1200.0,
        ),
        GadgetSensorDefinition(
            keys = setOf("co2", "airQuality", "mq135"),
            label = "CO2 / воздух",
            unit = "ppm",
            warningHigh = 1200.0,
            criticalHigh = 2500.0,
        ),
    )

    fun definitionFor(key: String): GadgetSensorDefinition? =
        definitions.firstOrNull { definition ->
            definition.keys.any { it.equals(key, ignoreCase = true) }
        }

    fun labelFor(key: String): String =
        definitionFor(key)?.label ?: key

    fun statusFor(key: String, value: Double?): DeviceSensorStatus =
        definitionFor(key)?.statusFor(value) ?: DeviceSensorStatus.UNKNOWN
}
