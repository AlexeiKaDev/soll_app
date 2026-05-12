package com.soll.data.device

import com.soll.domain.device.AquikProvisioningDefaults
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class DeviceProvisioningResult(
    val success: Boolean,
    val status: String,
    val message: String,
    val rawJson: String,
)

@Singleton
class DeviceProvisioningClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun configureWifi(
        host: String,
        ssid: String,
        password: String,
    ): Result<DeviceProvisioningResult> = postJson(
        host = host,
        path = AquikProvisioningDefaults.wifiConfigureEndpoint,
        body = JSONObject()
            .put("ssid", ssid)
            .put("password", password),
    )

    suspend fun startSmartConfig(
        host: String,
        timeoutSec: Int = AquikProvisioningDefaults.defaultSmartConfigTimeoutSec,
    ): Result<DeviceProvisioningResult> = postJson(
        host = host,
        path = AquikProvisioningDefaults.smartConfigEndpoint,
        body = JSONObject().put("timeout", timeoutSec),
    )

    suspend fun getConnectionStatus(host: String): Result<DeviceProvisioningResult> {
        val primary = getJson(host, AquikProvisioningDefaults.connectionStatusEndpoint)
        return if (primary.isSuccess) primary else getJson(host, "/api/status")
    }

    private suspend fun postJson(
        host: String,
        path: String,
        body: JSONObject,
    ): Result<DeviceProvisioningResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${baseUrl(host)}$path")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            execute(request)
        }
    }

    private suspend fun getJson(host: String, path: String): Result<DeviceProvisioningResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${baseUrl(host)}$path")
                .get()
                .build()
            execute(request)
        }
    }

    private fun execute(request: Request): DeviceProvisioningResult {
        okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty().ifBlank { "{}" }
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("message", raw) }
            val status = json.optString("status")
                .ifBlank { if (response.isSuccessful) "готово" else "ошибка" }
            val message = json.optString("message")
                .ifBlank { response.message.ifBlank { status } }
            if (!response.isSuccessful) {
                throw IllegalStateException(message)
            }
            return DeviceProvisioningResult(
                success = true,
                status = status,
                message = message,
                rawJson = json.toString(2),
            )
        }
    }

    private fun baseUrl(host: String): String {
        val clean = host.trim().trimEnd('/')
        return if (clean.startsWith("http://") || clean.startsWith("https://")) {
            clean
        } else {
            "http://$clean"
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
