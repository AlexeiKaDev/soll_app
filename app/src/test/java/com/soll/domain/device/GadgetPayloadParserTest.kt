package com.soll.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GadgetPayloadParserTest {
    @Test
    fun `telemetry parser adds labels units and status`() {
        val response = response(
            command = "getSensors",
            dataJson = """{"sensors":{"waterTemp":30.5,"humidity":44,"timestamp":1}}""",
        )

        val telemetry = GadgetPayloadParser.telemetry(response, "aquik-v2:host:81")

        assertEquals(2, telemetry.values.size)
        assertEquals("Темп. воды", telemetry.values[0].label)
        assertEquals("30.50 °C", telemetry.values[0].value)
        assertEquals(DeviceSensorStatus.WARNING, telemetry.values[0].status)
    }

    @Test
    fun `telemetry parser reads server map snapshots`() {
        val telemetry = GadgetPayloadParser.telemetry(
            deviceId = "aquik-cloud",
            values = mapOf("waterTemp" to 25.0, "tds" to 245, "timestamp" to 1),
            rawJson = "{}",
            timestamp = 1000,
        )

        assertEquals(2, telemetry.values.size)
        assertTrue(telemetry.values.any { it.label == "Темп. воды" && it.value == "25 °C" })
        assertTrue(telemetry.values.any { it.label == "TDS" && it.value == "245 ppm" })
    }

    @Test
    fun `actuator parser restores switch and slider state`() {
        val response = response(
            command = "getActuators",
            dataJson = """{"actuators":{"pump":true,"airPump":false,"fan":true,"lightBrightness":180}}""",
        )

        val snapshot = GadgetPayloadParser.actuators(response)

        assertEquals(false, snapshot.airPump)
        assertEquals(true, snapshot.waterPump)
        assertEquals(true, snapshot.fan)
        assertEquals(180, snapshot.fullLed)
    }

    @Test
    fun `config parser keeps useful settings and hides secrets`() {
        val response = response(
            command = "getSettings",
            dataJson = """{"deviceName":"Aquik","wifiPassword":"secret","sensorInterval":2000,"autoMode":true}""",
        )

        val summary = GadgetPayloadParser.config(response)

        assertTrue(summary.items.any { it.label == "Имя" && it.value == "Aquik" })
        assertTrue(summary.items.any { it.label == "Интервал датчиков" && it.value == "2000" })
        assertTrue(summary.items.none { it.value == "secret" })
    }

    @Test
    fun `schedule parser reads aquik schedules`() {
        val response = response(
            command = "getSchedules",
            dataJson = """{"schedules":[{"id":1,"name":"Свет утром","enabled":true,"type":"light","time":"08:00","action":"on"}]}""",
        )

        val summary = GadgetPayloadParser.schedules(response)

        assertEquals(1, summary.items.size)
        assertEquals("Свет утром", summary.items.first().name)
        assertEquals("08:00", summary.items.first().time)
    }

    @Test
    fun `diagnostic parser reads i2c scan result`() {
        val response = response(
            command = "scanI2C",
            dataJson = """{"devices":[{"addressHex":"0x76","name":"BME280"}],"count":1}""",
        )

        val summary = GadgetPayloadParser.diagnostics(response)

        assertTrue(summary.items.any { it.label == "I2C 0x76" && it.value == "BME280" })
        assertTrue(summary.items.any { it.label == "Найдено" && it.value == "1" })
    }

    private fun response(
        command: String,
        dataJson: String,
    ): DeviceCommandResponse =
        DeviceCommandResponse(
            requestId = 1,
            command = command,
            success = true,
            dataJson = dataJson,
            error = null,
            rawJson = """{"success":true,"data":$dataJson}""",
            timestamp = 1000,
        )
}
