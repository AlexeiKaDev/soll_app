package com.soll.data.repository

import android.content.Context
import com.soll.domain.activity.ActivitySample
import com.soll.domain.activity.ActivityTrackingSummaries
import com.soll.domain.activity.ActivityTrackingSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ActivityTrackingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _summary = MutableStateFlow(loadSummary())

    fun observeSummary(): StateFlow<ActivityTrackingSummary> = _summary.asStateFlow()

    val summary: ActivityTrackingSummary
        get() = _summary.value

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        settingsRepository.activityTrackerEnabled = enabled
        _summary.value = loadSummary()
    }

    @Synchronized
    fun recordSample(sample: ActivitySample) {
        val samples = (readSamples() + sample)
            .sortedBy { it.capturedAt }
            .takeLast(MAX_SAMPLES)
        writeSamples(samples)
        _summary.value = summarize(samples)
    }

    @Synchronized
    fun refresh() {
        _summary.value = loadSummary()
    }

    @Synchronized
    fun latestSample(): ActivitySample? =
        readSamples().maxByOrNull { it.capturedAt }

    private fun loadSummary(): ActivityTrackingSummary =
        summarize(readSamples())

    private fun summarize(samples: List<ActivitySample>): ActivityTrackingSummary =
        ActivityTrackingSummaries.summarize(samples).copy(enabled = settingsRepository.activityTrackerEnabled)

    private fun readSamples(): List<ActivitySample> {
        val raw = prefs.getString(KEY_SAMPLES_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toSample()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeSamples(samples: List<ActivitySample>) {
        val array = JSONArray()
        samples.forEach { sample -> array.put(sample.toJson()) }
        prefs.edit().putString(KEY_SAMPLES_JSON, array.toString()).apply()
    }

    private fun ActivitySample.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("session_id", sessionId)
            .put("captured_at", capturedAt)
            .putNullable("latitude", latitude)
            .putNullable("longitude", longitude)
            .putNullable("accuracy_meters", accuracyMeters)
            .put("step_delta", stepDelta)
            .putNullable("battery_level", batteryLevel)
            .put("is_charging", isCharging)
            .put("reason", reason)

    private fun JSONObject.toSample(): ActivitySample? =
        runCatching {
            ActivitySample(
                id = optString("id").takeIf { it.isNotBlank() } ?: return null,
                sessionId = optString("session_id").takeIf { it.isNotBlank() } ?: "activity",
                capturedAt = optLong("captured_at").takeIf { it > 0L } ?: return null,
                latitude = optNullableDouble("latitude"),
                longitude = optNullableDouble("longitude"),
                accuracyMeters = optNullableDouble("accuracy_meters")?.toFloat(),
                stepDelta = optInt("step_delta", 0).coerceAtLeast(0),
                batteryLevel = optNullableInt("battery_level"),
                isCharging = optBoolean("is_charging", false),
                reason = optString("reason", "imported"),
            )
        }.getOrNull()

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        if (value == null) {
            put(key, JSONObject.NULL)
        } else {
            put(key, value)
        }
        return this
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private companion object {
        const val PREFS_NAME = "activity_tracking"
        const val KEY_SAMPLES_JSON = "samples_json"
        const val MAX_SAMPLES = 1500
    }
}
