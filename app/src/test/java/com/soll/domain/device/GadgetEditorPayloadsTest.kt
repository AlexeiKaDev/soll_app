package com.soll.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class GadgetEditorPayloadsTest {
    @Test
    fun `settings payload keeps nested and top level values`() {
        val payload = JSONObject(
            GadgetEditorPayloads.settings(
                GadgetSettingsDraft(
                    deviceName = "Aquik",
                    timezone = "Europe/Chisinau",
                    sensorIntervalMs = 2000,
                    displayBrightness = 120,
                    autoMode = true,
                )
            )
        )

        assertEquals("Aquik", payload.getString("deviceName"))
        assertEquals(true, payload.getBoolean("autoMode"))
        assertEquals(2000, payload.getJSONObject("settings").getInt("sensorInterval"))
    }

    @Test
    fun `schedule and automation payloads include editable ids`() {
        val schedule = JSONObject(
            GadgetEditorPayloads.schedule(
                GadgetScheduleDraft(
                    id = "morning",
                    name = "Свет утром",
                    type = "light",
                    time = "08:00",
                    action = "on",
                    enabled = true,
                )
            )
        )
        val automation = JSONObject(
            GadgetEditorPayloads.automation(
                GadgetAutomationDraft(
                    id = "hot",
                    name = "Охлаждение",
                    sensorKey = "waterTemp",
                    operator = ">",
                    threshold = 28.0,
                    action = "fan:on",
                )
            )
        )

        assertEquals("morning", schedule.getString("id"))
        assertEquals("08:00", schedule.getJSONObject("schedule").getString("time"))
        assertEquals("hot", automation.getString("id"))
        assertEquals("waterTemp", automation.getJSONObject("rule").getJSONObject("condition").getString("sensor"))
        assertEquals("fan:on", automation.getString("action"))
    }

    @Test
    fun `calibration requires sensor key`() {
        val error = runCatching {
            GadgetEditorPayloads.calibration(GadgetCalibrationDraft())
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
