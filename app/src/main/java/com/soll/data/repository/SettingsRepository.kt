package com.soll.data.repository

import android.content.SharedPreferences
import com.soll.data.local.dao.BotConfigDao
import com.soll.data.local.entity.BotConfigEntity
import com.soll.data.notification.SystemNotificationImportanceMode
import com.soll.data.notification.SystemNotificationPreferences
import com.soll.domain.assistant.Capability
import com.soll.domain.assistant.CapabilitySettings
import com.soll.domain.assistant.proactive.ProactiveSuggestionFeedback
import com.soll.domain.deviceqa.DeviceQaCheckId
import com.soll.domain.deviceqa.DeviceQaManualResult
import com.soll.domain.deviceqa.DeviceQaStatus
import com.soll.domain.music.MusicRepeatMode
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.music.MusicSettings
import com.soll.domain.notes.NoteSettings
import com.soll.domain.scanner.ScannerDuplicatePolicy
import com.soll.domain.scanner.ScannerSettings
import com.soll.domain.tts.PiperProsodyPreset
import com.soll.domain.tts.TtsBookPerformanceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val botConfigDao: BotConfigDao
) : CapabilitySettings {
    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_LAST_OFFSET = "last_offset"
        private const val KEY_POLLING_TIMEOUT = "polling_timeout"
        private const val KEY_TTS_ENGINE = "tts_engine"
        private const val KEY_TTS_AUTO_ADVANCE = "tts_auto_advance"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_ENGINE_TYPE = "tts_engine_type"
        private const val KEY_TTS_SILERO_SPEAKER = "tts_silero_speaker"
        private const val KEY_TTS_UTROBIN_SPEAKER = "tts_utrobin_speaker"
        private const val KEY_TTS_UTROBIN_ORT_THREADS = "tts_utrobin_ort_threads"
        private const val KEY_TTS_NATASHA_ORT_THREADS = "tts_natasha_ort_threads"
        private const val KEY_TTS_CHATTERBOX_ORT_THREADS = "tts_chatterbox_ort_threads"
        private const val KEY_TTS_CHATTERBOX_EXAGGERATION = "tts_chatterbox_exaggeration"
        private const val KEY_TTS_CHATTERBOX_VOICE = "tts_chatterbox_voice"
        private const val KEY_TTS_SHERPA_NUM_THREADS = "tts_sherpa_num_threads"
        private const val KEY_TTS_PIPER_PROSODY_PRESET = "tts_piper_prosody_preset"
        private const val KEY_TTS_PIPER_PACK_ID = "tts_piper_pack_id"
        private const val KEY_TTS_NATASHA_PACK_ID = "tts_natasha_pack_id"
        private const val KEY_TTS_UTROBIN_PACK_ID = "tts_utrobin_pack_id"
        private const val KEY_TTS_CHATTERBOX_PACK_ID = "tts_chatterbox_pack_id"
        private const val KEY_TTS_BOOK_PERF_PROFILE = "tts_book_perf_profile"
        private const val KEY_BOOK_READER_S200_BOOTSTRAP = "book_reader_s200_bootstrap_done"
        private const val KEY_TTS_SYSTEM_PITCH = "tts_system_pitch"
        private const val KEY_TTS_ONNX_MODEL_ID = "tts_onnx_model_id"
        private const val KEY_TTS_ONNX_PRECISION = "tts_onnx_precision"
        /** Последний URI дерева для импортa ONNX-паков (SAF); для повтора «указать ту же папку». */
        private const val KEY_TTS_ONNX_IMPORT_TREE_URI = "tts_onnx_import_tree_uri"
        private const val KEY_TTS_MODEL_ROOT_URI = "tts_model_root_uri"
        private const val KEY_RISKY_CAPABILITIES_ENABLED = "risky_capabilities_enabled"
        private const val KEY_CAPABILITY_ENABLED_PREFIX = "capability_enabled_"
        private const val KEY_SOLL_SERVER_URL = "soll_server_url"
        private const val KEY_SOLL_API_PATH_PREFIX = "soll_api_path_prefix"
        private const val KEY_SOLL_RECOMMENDED_ENDPOINT_SEEDED = "soll_recommended_endpoint_seeded"
        private const val KEY_SOLL_ACCESS_TOKEN = "soll_access_token"
        private const val KEY_SOLL_DEVICE_ID = "soll_device_id"
        private const val KEY_SOLL_DEVICE_PAIRING_SECRET = "soll_device_pairing_secret"
        private const val KEY_SOLL_DEVICE_ACCESS_TOKEN = "soll_device_access_token"
        private const val KEY_SOLL_DEVICE_TOKEN_EXPIRES_AT = "soll_device_token_expires_at"
        private const val KEY_SOLL_SYNC_INTERVAL_MINUTES = "soll_sync_interval_minutes"
        private const val KEY_SOLL_WIFI_ONLY_UPLOAD = "soll_wifi_only_upload"
        private const val KEY_SOLL_CHAT_LAST_SEEN_MESSAGE_ID = "soll_chat_last_seen_message_id"
        private const val KEY_SOLL_TASK_BOARD_SIGNATURE = "soll_task_board_signature"
        private const val KEY_SOLL_PUSH_TOKEN = "soll_push_token"
        private const val KEY_SOLL_PUSH_TOKEN_REGISTERED_AT = "soll_push_token_registered_at"
        private const val KEY_SOLL_PUSH_TOKEN_LAST_ERROR = "soll_push_token_last_error"
        private const val KEY_SYSTEM_NOTIFICATION_IMPORTANCE_MODE = "system_notification_importance_mode"
        private const val KEY_SYSTEM_NOTIFICATION_CHANNEL_PREFIX = "system_notification_channel_"
        private const val KEY_PORTABLE_SSD_TREE_URI = "portable_ssd_tree_uri"
        private const val KEY_PORTABLE_SSD_LAST_ATTACH_NOTICE_AT = "portable_ssd_last_attach_notice_at"
        private const val KEY_VOICE_REQUIRES_UNLOCKED_DEVICE = "voice_requires_unlocked_device"
        private const val KEY_VOICE_REQUIRES_HEADSET = "voice_requires_headset"
        private const val KEY_VOICE_LOCAL_ONLY = "voice_local_only"
        private const val KEY_VOICE_WAKE_PHRASE_REQUIRED = "voice_wake_phrase_required"
        private const val KEY_MUSIC_RESUME_LAST_TRACK = "music_resume_last_track"
        private const val KEY_MUSIC_PAUSE_FOR_TTS = "music_pause_for_tts"
        private const val KEY_MUSIC_STOP_TTS_ON_START = "music_stop_tts_on_start"
        private const val KEY_MUSIC_HEADSET_CONTROLS = "music_headset_controls"
        private const val KEY_MUSIC_AUTO_RESCAN_ON_OPEN = "music_auto_rescan_on_open"
        private const val KEY_MUSIC_STRICT_AUDIO_FILTER = "music_strict_audio_filter"
        private const val KEY_MUSIC_SHOW_BACKGROUND_HINTS = "music_show_background_hints"
        private const val KEY_MUSIC_DEFAULT_SHUFFLE = "music_default_shuffle"
        private const val KEY_MUSIC_DEFAULT_REPEAT_MODE = "music_default_repeat_mode"
        private const val KEY_SCANNER_DUPLICATE_POLICY = "scanner_duplicate_policy"
        private const val KEY_NOTE_AUTO_SYNC = "note_auto_sync"
        private const val KEY_NOTE_WIFI_ONLY = "note_wifi_only"
        private const val KEY_NOTE_KEEP_LOCAL_AFTER_SYNC = "note_keep_local_after_sync"
        private const val KEY_NOTE_DEFAULT_TAGS = "note_default_tags"
        private const val KEY_DEVICE_AUTH_TOKEN_PREFIX = "device_auth_token_"
        private const val KEY_PROACTIVE_SUGGESTIONS_ENABLED = "proactive_suggestions_enabled"
        private const val KEY_PROACTIVE_SUGGESTIONS_DAILY_LIMIT = "proactive_suggestions_daily_limit"
        private const val KEY_PROACTIVE_SYSTEM_DELIVERY_ENABLED = "proactive_system_delivery_enabled"
        private const val KEY_PROACTIVE_TELEGRAM_DELIVERY_ENABLED = "proactive_telegram_delivery_enabled"
        private const val KEY_PROACTIVE_ACCEPTED_PREFIX = "proactive_accepted_at_"
        private const val KEY_PROACTIVE_DISMISSED_PREFIX = "proactive_dismissed_at_"
        private const val KEY_PROACTIVE_SNOOZED_PREFIX = "proactive_snoozed_until_"
        private const val KEY_PROACTIVE_DELIVERED_PREFIX = "proactive_delivered_at_"
        private const val KEY_ASSISTANT_MEMORY_ENABLED = "assistant_memory_enabled"
        private const val KEY_DEVICE_QA_STATUS_PREFIX = "device_qa_status_"
        private const val KEY_DEVICE_QA_CHECKED_AT_PREFIX = "device_qa_checked_at_"
        private const val KEY_DEVICE_QA_DEVICE_PREFIX = "device_qa_device_"
        private const val KEY_APP_THEME_VARIANT = "app_theme_variant"
        private const val KEY_APP_THEME_DEFAULT_MIGRATED = "app_theme_default_migrated_to_soll_v2"
        private const val DEFAULT_APP_THEME_VARIANT = "soll"
        const val RECOMMENDED_SOLL_SERVER_URL = "https://sales.monolith-ost.com/"
        const val RECOMMENDED_SOLL_API_PATH_PREFIX = "api/v1/soll"
        private const val KEY_ACTIVITY_TRACKER_ENABLED = "activity_tracker_enabled"
        private const val SUGGESTION_SNOOZE_MS = 2 * 60 * 60_000L
        private const val DAY_MS = 24 * 60 * 60_000L
    }

    init {
        migrateDefaultThemeVariant()
        seedRecommendedSollEndpoint()
    }

    private val _appThemeVariantFlow = MutableStateFlow(readAppThemeVariant())
    val appThemeVariantFlow: StateFlow<String> = _appThemeVariantFlow.asStateFlow()

    var appThemeVariant: String
        get() = readAppThemeVariant()
        set(value) {
            val normalized = normalizeAppThemeVariant(value)
            sharedPreferences.edit().putString(KEY_APP_THEME_VARIANT, normalized).apply()
            _appThemeVariantFlow.value = normalized
        }

    var activityTrackerEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_ACTIVITY_TRACKER_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ACTIVITY_TRACKER_ENABLED, value).apply()


    // Bot Token (encrypted storage)
    var botToken: String?
        get() = sharedPreferences.getString(KEY_BOT_TOKEN, null)
        set(value) = sharedPreferences.edit().putString(KEY_BOT_TOKEN, value).apply()

    // Auto-start on boot
    var autoStartEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_START, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_START, value).apply()

    // Service running state
    var isServiceRunning: Boolean
        get() = sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    // Last update offset for polling
    var lastOffset: Long
        get() = sharedPreferences.getLong(KEY_LAST_OFFSET, 0)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_OFFSET, value).apply()

    // Polling timeout in seconds
    var pollingTimeout: Int
        get() = sharedPreferences.getInt(KEY_POLLING_TIMEOUT, 30)
        set(value) = sharedPreferences.edit().putInt(KEY_POLLING_TIMEOUT, value).apply()

    // TTS engine package name
    var ttsEngine: String?
        get() = sharedPreferences.getString(KEY_TTS_ENGINE, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_ENGINE, value).apply()

    // TTS auto-advance to next chapter
    var ttsAutoAdvance: Boolean
        get() = sharedPreferences.getBoolean(KEY_TTS_AUTO_ADVANCE, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_TTS_AUTO_ADVANCE, value).apply()

    // TTS speech rate
    var ttsSpeechRate: Float
        get() = sharedPreferences.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)
        set(value) = sharedPreferences.edit().putFloat(KEY_TTS_SPEECH_RATE, value).apply()

    // TTS engine type: "system" or "silero"
    var ttsEngineType: String
        get() = sharedPreferences.getString(KEY_TTS_ENGINE_TYPE, "system") ?: "system"
        set(value) = sharedPreferences.edit().putString(KEY_TTS_ENGINE_TYPE, value).apply()

    // Piper / Silero voice id (e.g. irina)
    var ttssileroSpeaker: String
        get() = sharedPreferences.getString(KEY_TTS_SILERO_SPEAKER, "irina") ?: "irina"
        set(value) = sharedPreferences.edit().putString(KEY_TTS_SILERO_SPEAKER, value).apply()

    /** Utrobin speaker index as string: "0" / "1" */
    var ttsUtrobinSpeaker: String
        get() = sharedPreferences.getString(KEY_TTS_UTROBIN_SPEAKER, "0") ?: "0"
        set(value) = sharedPreferences.edit().putString(KEY_TTS_UTROBIN_SPEAKER, value).apply()

    /** ONNX Runtime intra-op threads for Utrobin (1–4). */
    var ttsUtrobinOrtIntraThreads: Int
        get() = sharedPreferences.getInt(KEY_TTS_UTROBIN_ORT_THREADS, 2).coerceIn(1, 4)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_TTS_UTROBIN_ORT_THREADS, value.coerceIn(1, 4))
            .apply()

    /** ONNX intra-op threads for Natasha book engine (1–4). */
    var ttsNatashaOrtIntraThreads: Int
        get() = sharedPreferences.getInt(KEY_TTS_NATASHA_ORT_THREADS, 2).coerceIn(1, 4)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_TTS_NATASHA_ORT_THREADS, value.coerceIn(1, 4))
            .apply()

    /** ONNX intra-op threads for Chatterbox book engine (1–4). */
    var ttsChatterboxOrtIntraThreads: Int
        get() = sharedPreferences.getInt(KEY_TTS_CHATTERBOX_ORT_THREADS, 2).coerceIn(1, 4)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_TTS_CHATTERBOX_ORT_THREADS, value.coerceIn(1, 4))
            .apply()

    /** Chatterbox emotion exaggeration (0.3–0.9). */
    var ttsChatterboxExaggeration: Float
        get() = sharedPreferences.getFloat(KEY_TTS_CHATTERBOX_EXAGGERATION, 0.5f).coerceIn(0.3f, 0.9f)
        set(value) = sharedPreferences.edit()
            .putFloat(KEY_TTS_CHATTERBOX_EXAGGERATION, value.coerceIn(0.3f, 0.9f))
            .apply()

    /** Chatterbox reference voice id (= wav file name without extension). */
    var ttsChatterboxVoice: String?
        get() = sharedPreferences.getString(KEY_TTS_CHATTERBOX_VOICE, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_CHATTERBOX_VOICE, value).apply()

    /** Sherpa OfflineTts numThreads for Piper (1–4). */
    var ttsSherpaNumThreads: Int
        get() = sharedPreferences.getInt(
            KEY_TTS_SHERPA_NUM_THREADS,
            TtsBookPerformanceProfile.sherpaNumThreads(
                TtsBookPerformanceProfile.BALANCED,
                Runtime.getRuntime().availableProcessors(),
            ),
        ).coerceIn(1, 4)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_TTS_SHERPA_NUM_THREADS, value.coerceIn(1, 4))
            .apply()

    var ttsPiperProsodyPreset: String
        get() = sharedPreferences.getString(
            KEY_TTS_PIPER_PROSODY_PRESET,
            PiperProsodyPreset.DEFAULT.storageKey,
        ) ?: PiperProsodyPreset.DEFAULT.storageKey
        set(value) = sharedPreferences.edit()
            .putString(KEY_TTS_PIPER_PROSODY_PRESET, PiperProsodyPreset.fromStorage(value).storageKey)
            .apply()

    var ttsPiperPackId: String?
        get() = sharedPreferences.getString(KEY_TTS_PIPER_PACK_ID, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_PIPER_PACK_ID, value).apply()

    var ttsNatashaPackId: String?
        get() = sharedPreferences.getString(KEY_TTS_NATASHA_PACK_ID, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_NATASHA_PACK_ID, value).apply()

    var ttsUtrobinPackId: String?
        get() = sharedPreferences.getString(KEY_TTS_UTROBIN_PACK_ID, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_UTROBIN_PACK_ID, value).apply()

    var ttsChatterboxPackId: String?
        get() = sharedPreferences.getString(KEY_TTS_CHATTERBOX_PACK_ID, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_CHATTERBOX_PACK_ID, value).apply()

    /**
     * Book reader preset: battery / balanced / quality (threads + chunk merge).
     * See [com.soll.domain.tts.TtsBookPerformanceProfile] and docs/tts-s200-model-shortlist.md.
     */
    var ttsBookPerformanceProfile: String
        get() = sharedPreferences.getString(
            KEY_TTS_BOOK_PERF_PROFILE,
            TtsBookPerformanceProfile.BALANCED.storageKey,
        ) ?: TtsBookPerformanceProfile.BALANCED.storageKey
        set(value) = sharedPreferences.edit().putString(KEY_TTS_BOOK_PERF_PROFILE, value).apply()

    /** One-time defaults for Doogee S200–class devices (Piper + Balanced). */
    var bookReaderS200BootstrapDone: Boolean
        get() = sharedPreferences.getBoolean(KEY_BOOK_READER_S200_BOOTSTRAP, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_BOOK_READER_S200_BOOTSTRAP, value).apply()

    /** Writes thread prefs to match a performance preset (e.g. when user taps Battery/Balanced/Quality). */
    fun syncThreadPrefsFromProfile(profile: TtsBookPerformanceProfile) {
        val ort = TtsBookPerformanceProfile.ortIntraThreads(profile)
        val sh = TtsBookPerformanceProfile.sherpaNumThreads(
            profile,
            Runtime.getRuntime().availableProcessors(),
        )
        sharedPreferences.edit()
            .putInt(KEY_TTS_UTROBIN_ORT_THREADS, ort)
            .putInt(KEY_TTS_NATASHA_ORT_THREADS, ort)
            .putInt(KEY_TTS_CHATTERBOX_ORT_THREADS, ort)
            .putInt(KEY_TTS_SHERPA_NUM_THREADS, sh)
            .apply()
    }

    /** Тон системного TTS (1 = значение по умолчанию). */
    var ttsSystemPitch: Float
        get() = sharedPreferences.getFloat(KEY_TTS_SYSTEM_PITCH, 1.0f)
        set(value) = sharedPreferences.edit().putFloat(KEY_TTS_SYSTEM_PITCH, value).apply()

    var ttsOnnxModelId: String?
        get() = sharedPreferences.getString(KEY_TTS_ONNX_MODEL_ID, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_ONNX_MODEL_ID, value).apply()

    var ttsOnnxPrecision: String?
        get() = sharedPreferences.getString(KEY_TTS_ONNX_PRECISION, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_ONNX_PRECISION, value).apply()

    var ttsOnnxImportTreeUri: String?
        get() = sharedPreferences.getString(KEY_TTS_ONNX_IMPORT_TREE_URI, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_ONNX_IMPORT_TREE_URI, value).apply()

    var ttsModelRootUri: String?
        get() = sharedPreferences.getString(KEY_TTS_MODEL_ROOT_URI, ttsOnnxImportTreeUri)
        set(value) = sharedPreferences.edit()
            .putString(KEY_TTS_MODEL_ROOT_URI, value)
            .putString(KEY_TTS_ONNX_IMPORT_TREE_URI, value)
            .apply()

    override fun isRiskyCapabilitiesEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_RISKY_CAPABILITIES_ENABLED, true)

    fun setRiskyCapabilitiesEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_RISKY_CAPABILITIES_ENABLED, enabled).apply()
    }

    override fun isCapabilityEnabled(capability: Capability): Boolean =
        sharedPreferences.getBoolean(capabilityEnabledKey(capability.id), capability.enabledByDefault)

    fun setCapabilityEnabled(capabilityId: String, enabled: Boolean) {
        sharedPreferences.edit().putBoolean(capabilityEnabledKey(capabilityId), enabled).apply()
    }

    private fun capabilityEnabledKey(capabilityId: String): String =
        KEY_CAPABILITY_ENABLED_PREFIX + capabilityId.lowercase()

    var sollServerUrl: String
        get() = sharedPreferences.getString(KEY_SOLL_SERVER_URL, "")
            ?.trim()
            ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_SERVER_URL, value.trim()).apply()

    var sollApiPathPrefix: String
        get() = sharedPreferences.getString(KEY_SOLL_API_PATH_PREFIX, "")
            ?.trim()
            ?.trim('/')
            ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_API_PATH_PREFIX, value.trim().trim('/')).apply()

    var sollAccessToken: String
        get() = sharedPreferences.getString(KEY_SOLL_ACCESS_TOKEN, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_ACCESS_TOKEN, value.trim()).apply()

    var sollDeviceId: String
        get() = sharedPreferences.getString(KEY_SOLL_DEVICE_ID, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_DEVICE_ID, value.trim()).apply()

    var sollDevicePairingSecret: String
        get() = sharedPreferences.getString(KEY_SOLL_DEVICE_PAIRING_SECRET, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_DEVICE_PAIRING_SECRET, value.trim()).apply()

    var sollDeviceAccessToken: String
        get() = sharedPreferences.getString(KEY_SOLL_DEVICE_ACCESS_TOKEN, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_DEVICE_ACCESS_TOKEN, value.trim()).apply()

    var sollDeviceTokenExpiresAt: String
        get() = sharedPreferences.getString(KEY_SOLL_DEVICE_TOKEN_EXPIRES_AT, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_DEVICE_TOKEN_EXPIRES_AT, value.trim()).apply()

    var sollSyncIntervalMinutes: Int
        get() = sharedPreferences.getInt(KEY_SOLL_SYNC_INTERVAL_MINUTES, 60).coerceIn(5, 1440)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_SOLL_SYNC_INTERVAL_MINUTES, value.coerceIn(5, 1440))
            .apply()

    var sollWifiOnlyUpload: Boolean
        get() = sharedPreferences.getBoolean(KEY_SOLL_WIFI_ONLY_UPLOAD, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SOLL_WIFI_ONLY_UPLOAD, value).apply()

    var sollChatLastSeenMessageId: Long
        get() = sharedPreferences.getLong(KEY_SOLL_CHAT_LAST_SEEN_MESSAGE_ID, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_SOLL_CHAT_LAST_SEEN_MESSAGE_ID, value.coerceAtLeast(0L)).apply()

    fun advanceSollChatLastSeenMessageId(messageId: Long) {
        if (messageId > sollChatLastSeenMessageId) {
            sollChatLastSeenMessageId = messageId
        }
    }

    var sollTaskBoardSignature: String
        get() = sharedPreferences.getString(KEY_SOLL_TASK_BOARD_SIGNATURE, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_TASK_BOARD_SIGNATURE, value).apply()

    var sollPushToken: String
        get() = sharedPreferences.getString(KEY_SOLL_PUSH_TOKEN, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_PUSH_TOKEN, value.trim()).apply()

    var sollPushTokenRegisteredAt: Long
        get() = sharedPreferences.getLong(KEY_SOLL_PUSH_TOKEN_REGISTERED_AT, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_SOLL_PUSH_TOKEN_REGISTERED_AT, value.coerceAtLeast(0L)).apply()

    var sollPushTokenLastError: String
        get() = sharedPreferences.getString(KEY_SOLL_PUSH_TOKEN_LAST_ERROR, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SOLL_PUSH_TOKEN_LAST_ERROR, value.take(300)).apply()

    var systemNotificationImportanceMode: SystemNotificationImportanceMode
        get() = SystemNotificationImportanceMode.fromStorage(
            sharedPreferences.getString(KEY_SYSTEM_NOTIFICATION_IMPORTANCE_MODE, null),
        )
        set(value) = sharedPreferences.edit()
            .putString(KEY_SYSTEM_NOTIFICATION_IMPORTANCE_MODE, value.storageKey)
            .apply()

    fun isSystemNotificationChannelEnabled(channel: SollNotificationChannel): Boolean =
        sharedPreferences.getBoolean(
            systemNotificationChannelKey(channel),
            channel in SystemNotificationPreferences.DEFAULT_ALLOWED_CHANNELS,
        )

    fun setSystemNotificationChannelEnabled(channel: SollNotificationChannel, enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(systemNotificationChannelKey(channel), enabled)
            .apply()
    }

    fun systemNotificationPreferences(): SystemNotificationPreferences =
        SystemNotificationPreferences(
            importanceMode = systemNotificationImportanceMode,
            allowedChannels = SystemNotificationPreferences.FILTERABLE_CHANNELS
                .filter { isSystemNotificationChannelEnabled(it) }
                .toSet(),
        )

    private fun systemNotificationChannelKey(channel: SollNotificationChannel): String =
        KEY_SYSTEM_NOTIFICATION_CHANNEL_PREFIX + channel.name.lowercase()

    fun shouldRegisterSollPushToken(token: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return false
        if (cleanToken != sollPushToken) return true
        return nowMillis - sollPushTokenRegisteredAt > DAY_MS
    }

    fun markSollPushTokenRegistered(token: String, nowMillis: Long = System.currentTimeMillis()) {
        sharedPreferences.edit()
            .putString(KEY_SOLL_PUSH_TOKEN, token.trim())
            .putLong(KEY_SOLL_PUSH_TOKEN_REGISTERED_AT, nowMillis)
            .putString(KEY_SOLL_PUSH_TOKEN_LAST_ERROR, "")
            .apply()
    }

    var portableSsdTreeUri: String?
        get() = sharedPreferences.getString(KEY_PORTABLE_SSD_TREE_URI, null)
        set(value) = sharedPreferences.edit().putString(KEY_PORTABLE_SSD_TREE_URI, value).apply()

    var portableSsdLastAttachNoticeAt: Long
        get() = sharedPreferences.getLong(KEY_PORTABLE_SSD_LAST_ATTACH_NOTICE_AT, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_PORTABLE_SSD_LAST_ATTACH_NOTICE_AT, value).apply()

    var voiceRequiresUnlockedDevice: Boolean
        get() = sharedPreferences.getBoolean(KEY_VOICE_REQUIRES_UNLOCKED_DEVICE, true)
        set(value) = sharedPreferences.edit()
            .putBoolean(KEY_VOICE_REQUIRES_UNLOCKED_DEVICE, value)
            .apply()

    var voiceRequiresHeadset: Boolean
        get() = sharedPreferences.getBoolean(KEY_VOICE_REQUIRES_HEADSET, false)
        set(value) = sharedPreferences.edit()
            .putBoolean(KEY_VOICE_REQUIRES_HEADSET, value)
            .apply()

    var voiceLocalOnly: Boolean
        get() = sharedPreferences.getBoolean(KEY_VOICE_LOCAL_ONLY, false)
        set(value) = sharedPreferences.edit()
            .putBoolean(KEY_VOICE_LOCAL_ONLY, value)
            .apply()

    var voiceWakePhraseRequired: Boolean
        get() = sharedPreferences.getBoolean(KEY_VOICE_WAKE_PHRASE_REQUIRED, false)
        set(value) = sharedPreferences.edit()
            .putBoolean(KEY_VOICE_WAKE_PHRASE_REQUIRED, value)
            .apply()

    var musicResumeLastTrack: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUSIC_RESUME_LAST_TRACK, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUSIC_RESUME_LAST_TRACK, value).apply()

    var musicPauseForTts: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUSIC_PAUSE_FOR_TTS, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUSIC_PAUSE_FOR_TTS, value).apply()

    var musicStopTtsOnStart: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUSIC_STOP_TTS_ON_START, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUSIC_STOP_TTS_ON_START, value).apply()

    var musicHeadsetControls: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUSIC_HEADSET_CONTROLS, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUSIC_HEADSET_CONTROLS, value).apply()

    var musicAutoRescanOnOpen: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUSIC_AUTO_RESCAN_ON_OPEN, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUSIC_AUTO_RESCAN_ON_OPEN, value).apply()

    var musicStrictAudioFilter: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUSIC_STRICT_AUDIO_FILTER, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUSIC_STRICT_AUDIO_FILTER, value).apply()

    var musicShowBackgroundHints: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUSIC_SHOW_BACKGROUND_HINTS, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUSIC_SHOW_BACKGROUND_HINTS, value).apply()

    var musicDefaultShuffle: Boolean
        get() = sharedPreferences.getBoolean(KEY_MUSIC_DEFAULT_SHUFFLE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_MUSIC_DEFAULT_SHUFFLE, value).apply()

    var musicDefaultRepeatMode: MusicRepeatMode
        get() = runCatching {
            MusicRepeatMode.valueOf(
                sharedPreferences.getString(KEY_MUSIC_DEFAULT_REPEAT_MODE, MusicRepeatMode.OFF.name)
                    ?: MusicRepeatMode.OFF.name
            )
        }.getOrDefault(MusicRepeatMode.OFF)
        set(value) = sharedPreferences.edit().putString(KEY_MUSIC_DEFAULT_REPEAT_MODE, value.name).apply()

    fun getMusicSettings(): MusicSettings = MusicSettings(
        resumeLastTrack = musicResumeLastTrack,
        pauseMusicForTts = musicPauseForTts,
        stopTtsOnMusicStart = musicStopTtsOnStart,
        headsetControlsEnabled = musicHeadsetControls,
        autoRescanOnOpen = musicAutoRescanOnOpen,
        strictAudioFilter = musicStrictAudioFilter,
        showBackgroundHints = musicShowBackgroundHints,
        defaultShuffle = musicDefaultShuffle,
        defaultRepeatMode = musicDefaultRepeatMode,
    )

    fun saveMusicSettings(settings: MusicSettings) {
        sharedPreferences.edit()
            .putBoolean(KEY_MUSIC_RESUME_LAST_TRACK, settings.resumeLastTrack)
            .putBoolean(KEY_MUSIC_PAUSE_FOR_TTS, settings.pauseMusicForTts)
            .putBoolean(KEY_MUSIC_STOP_TTS_ON_START, settings.stopTtsOnMusicStart)
            .putBoolean(KEY_MUSIC_HEADSET_CONTROLS, settings.headsetControlsEnabled)
            .putBoolean(KEY_MUSIC_AUTO_RESCAN_ON_OPEN, settings.autoRescanOnOpen)
            .putBoolean(KEY_MUSIC_STRICT_AUDIO_FILTER, settings.strictAudioFilter)
            .putBoolean(KEY_MUSIC_SHOW_BACKGROUND_HINTS, settings.showBackgroundHints)
            .putBoolean(KEY_MUSIC_DEFAULT_SHUFFLE, settings.defaultShuffle)
            .putString(KEY_MUSIC_DEFAULT_REPEAT_MODE, settings.defaultRepeatMode.name)
            .apply()
    }

    var scannerDuplicatePolicy: ScannerDuplicatePolicy
        get() = ScannerDuplicatePolicy.fromStorage(
            sharedPreferences.getString(KEY_SCANNER_DUPLICATE_POLICY, null),
        )
        set(value) = sharedPreferences.edit()
            .putString(KEY_SCANNER_DUPLICATE_POLICY, value.storageKey)
            .apply()

    fun getScannerSettings(): ScannerSettings = ScannerSettings(
        duplicatePolicy = scannerDuplicatePolicy,
    )

    fun saveScannerSettings(settings: ScannerSettings) {
        sharedPreferences.edit()
            .putString(KEY_SCANNER_DUPLICATE_POLICY, settings.duplicatePolicy.storageKey)
            .apply()
    }

    var noteAutoSync: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTE_AUTO_SYNC, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_NOTE_AUTO_SYNC, value).apply()

    var noteWifiOnly: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTE_WIFI_ONLY, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_NOTE_WIFI_ONLY, value).apply()

    var noteKeepLocalAfterSync: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTE_KEEP_LOCAL_AFTER_SYNC, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_NOTE_KEEP_LOCAL_AFTER_SYNC, value).apply()

    var noteDefaultTags: String
        get() = sharedPreferences.getString(KEY_NOTE_DEFAULT_TAGS, "mobile, заметки") ?: "mobile, заметки"
        set(value) = sharedPreferences.edit().putString(KEY_NOTE_DEFAULT_TAGS, value.trim()).apply()

    fun getNoteSettings(): NoteSettings = NoteSettings(
        autoSync = noteAutoSync,
        wifiOnly = noteWifiOnly,
        keepLocalAfterSync = noteKeepLocalAfterSync,
        defaultTags = noteDefaultTags,
    )

    fun saveNoteSettings(settings: NoteSettings) {
        sharedPreferences.edit()
            .putBoolean(KEY_NOTE_AUTO_SYNC, settings.autoSync)
            .putBoolean(KEY_NOTE_WIFI_ONLY, settings.wifiOnly)
            .putBoolean(KEY_NOTE_KEEP_LOCAL_AFTER_SYNC, settings.keepLocalAfterSync)
            .putString(KEY_NOTE_DEFAULT_TAGS, settings.defaultTags.trim())
            .apply()
    }

    fun getDeviceAuthToken(deviceId: String): String =
        sharedPreferences.getString(deviceAuthTokenKey(deviceId), "") ?: ""

    fun setDeviceAuthToken(deviceId: String, token: String) {
        val key = deviceAuthTokenKey(deviceId)
        sharedPreferences.edit().apply {
            if (token.isBlank()) {
                remove(key)
            } else {
                putString(key, token.trim())
            }
        }.apply()
    }

    private fun deviceAuthTokenKey(deviceId: String): String =
        KEY_DEVICE_AUTH_TOKEN_PREFIX + deviceId.lowercase()
            .replace(Regex("[^a-z0-9_.:-]"), "_")

    var proactiveSuggestionsEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_PROACTIVE_SUGGESTIONS_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_PROACTIVE_SUGGESTIONS_ENABLED, value).apply()

    var proactiveSuggestionsDailyLimit: Int
        get() = sharedPreferences.getInt(KEY_PROACTIVE_SUGGESTIONS_DAILY_LIMIT, 3).coerceIn(1, 6)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_PROACTIVE_SUGGESTIONS_DAILY_LIMIT, value.coerceIn(1, 6))
            .apply()

    var proactiveSystemDeliveryEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_PROACTIVE_SYSTEM_DELIVERY_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_PROACTIVE_SYSTEM_DELIVERY_ENABLED, value).apply()

    var proactiveTelegramDeliveryEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_PROACTIVE_TELEGRAM_DELIVERY_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_PROACTIVE_TELEGRAM_DELIVERY_ENABLED, value).apply()

    fun isProactiveSuggestionSuppressed(suggestionId: String, nowMillis: Long): Boolean {
        val cleanId = suggestionId.suggestionKey()
        val snoozedUntil = sharedPreferences.getLong(KEY_PROACTIVE_SNOOZED_PREFIX + cleanId, 0L)
        if (snoozedUntil > nowMillis) return true

        val currentDay = nowMillis / DAY_MS
        val dismissedAt = sharedPreferences.getLong(KEY_PROACTIVE_DISMISSED_PREFIX + cleanId, 0L)
        if (dismissedAt > 0L && dismissedAt / DAY_MS == currentDay) return true

        val acceptedAt = sharedPreferences.getLong(KEY_PROACTIVE_ACCEPTED_PREFIX + cleanId, 0L)
        return acceptedAt > 0L && acceptedAt / DAY_MS == currentDay
    }

    fun recordProactiveSuggestionFeedback(
        suggestionId: String,
        feedback: ProactiveSuggestionFeedback,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val cleanId = suggestionId.suggestionKey()
        sharedPreferences.edit().apply {
            when (feedback) {
                ProactiveSuggestionFeedback.ACCEPTED -> {
                    putLong(KEY_PROACTIVE_ACCEPTED_PREFIX + cleanId, nowMillis)
                    remove(KEY_PROACTIVE_SNOOZED_PREFIX + cleanId)
                }
                ProactiveSuggestionFeedback.DISMISSED -> {
                    putLong(KEY_PROACTIVE_DISMISSED_PREFIX + cleanId, nowMillis)
                    remove(KEY_PROACTIVE_SNOOZED_PREFIX + cleanId)
                }
                ProactiveSuggestionFeedback.SNOOZED -> {
                    putLong(KEY_PROACTIVE_SNOOZED_PREFIX + cleanId, nowMillis + SUGGESTION_SNOOZE_MS)
                }
            }
        }.apply()
    }

    fun shouldDeliverProactiveSuggestion(
        suggestionId: String,
        deliveryTarget: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val key = proactiveDeliveryKey(suggestionId, deliveryTarget)
        val currentDay = nowMillis / DAY_MS
        val deliveredAt = sharedPreferences.getLong(key, 0L)
        return deliveredAt == 0L || deliveredAt / DAY_MS != currentDay
    }

    fun recordProactiveSuggestionDelivered(
        suggestionId: String,
        deliveryTarget: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        sharedPreferences.edit()
            .putLong(proactiveDeliveryKey(suggestionId, deliveryTarget), nowMillis)
            .apply()
    }

    private fun String.suggestionKey(): String =
        lowercase().replace(Regex("[^a-z0-9_.:-]"), "_")

    private fun proactiveDeliveryKey(suggestionId: String, deliveryTarget: String): String =
        KEY_PROACTIVE_DELIVERED_PREFIX + deliveryTarget.suggestionKey() + "_" + suggestionId.suggestionKey()

    var assistantMemoryEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_ASSISTANT_MEMORY_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ASSISTANT_MEMORY_ENABLED, value).apply()

    fun getDeviceQaManualResult(id: DeviceQaCheckId): DeviceQaManualResult? {
        val status = sharedPreferences.getString(deviceQaStatusKey(id), null)
            ?.let { runCatching { DeviceQaStatus.valueOf(it) }.getOrNull() }
            ?: return null
        if (status != DeviceQaStatus.MANUAL_OK && status != DeviceQaStatus.MANUAL_PROBLEM) return null
        val checkedAt = sharedPreferences.getLong(deviceQaCheckedAtKey(id), 0L)
        if (checkedAt <= 0L) return null
        val deviceSummary = sharedPreferences.getString(deviceQaDeviceKey(id), null)
            ?.takeIf { it.isNotBlank() }
        return DeviceQaManualResult(
            status = status,
            checkedAt = checkedAt,
            deviceSummary = deviceSummary,
        )
    }

    fun setDeviceQaManualResult(id: DeviceQaCheckId, result: DeviceQaManualResult) {
        sharedPreferences.edit()
            .putString(deviceQaStatusKey(id), result.status.name)
            .putLong(deviceQaCheckedAtKey(id), result.checkedAt)
            .putString(deviceQaDeviceKey(id), result.deviceSummary.orEmpty())
            .apply()
    }

    fun clearDeviceQaManualResult(id: DeviceQaCheckId) {
        sharedPreferences.edit()
            .remove(deviceQaStatusKey(id))
            .remove(deviceQaCheckedAtKey(id))
            .remove(deviceQaDeviceKey(id))
            .apply()
    }

    private fun deviceQaStatusKey(id: DeviceQaCheckId): String =
        KEY_DEVICE_QA_STATUS_PREFIX + id.storageKey

    private fun deviceQaCheckedAtKey(id: DeviceQaCheckId): String =
        KEY_DEVICE_QA_CHECKED_AT_PREFIX + id.storageKey

    private fun deviceQaDeviceKey(id: DeviceQaCheckId): String =
        KEY_DEVICE_QA_DEVICE_PREFIX + id.storageKey

    private fun migrateDefaultThemeVariant() {
        if (sharedPreferences.getBoolean(KEY_APP_THEME_DEFAULT_MIGRATED, false)) {
            return
        }
        val current = sharedPreferences.getString(KEY_APP_THEME_VARIANT, null)
        sharedPreferences.edit().apply {
            if (current == null || current in setOf("classic", "aurora", "aquik")) {
                putString(KEY_APP_THEME_VARIANT, DEFAULT_APP_THEME_VARIANT)
            }
            putBoolean(KEY_APP_THEME_DEFAULT_MIGRATED, true)
            apply()
        }
    }

    fun resetSollEndpointToRecommended() {
        sharedPreferences.edit()
            .putString(KEY_SOLL_SERVER_URL, RECOMMENDED_SOLL_SERVER_URL)
            .putString(KEY_SOLL_API_PATH_PREFIX, RECOMMENDED_SOLL_API_PATH_PREFIX)
            .putBoolean(KEY_SOLL_RECOMMENDED_ENDPOINT_SEEDED, true)
            .apply()
    }

    private fun seedRecommendedSollEndpoint() {
        if (sharedPreferences.getBoolean(KEY_SOLL_RECOMMENDED_ENDPOINT_SEEDED, false)) {
            return
        }
        val editor = sharedPreferences.edit()
        if (!sharedPreferences.contains(KEY_SOLL_SERVER_URL)) {
            editor.putString(KEY_SOLL_SERVER_URL, RECOMMENDED_SOLL_SERVER_URL)
        }
        if (!sharedPreferences.contains(KEY_SOLL_API_PATH_PREFIX)) {
            editor.putString(KEY_SOLL_API_PATH_PREFIX, RECOMMENDED_SOLL_API_PATH_PREFIX)
        }
        editor.putBoolean(KEY_SOLL_RECOMMENDED_ENDPOINT_SEEDED, true).apply()
    }

    private fun readAppThemeVariant(): String =
        normalizeAppThemeVariant(sharedPreferences.getString(KEY_APP_THEME_VARIANT, DEFAULT_APP_THEME_VARIANT))

    private fun normalizeAppThemeVariant(value: String?): String =
        when (value) {
            "soll", "classic", "aurora", "aquik" -> value
            else -> DEFAULT_APP_THEME_VARIANT
        }

    // Bot configs from database
    fun getAllBotConfigs(): Flow<List<BotConfigEntity>> = botConfigDao.getAllConfigs()

    suspend fun getActiveConfig(): BotConfigEntity? = botConfigDao.getActiveConfig()

    fun getActiveConfigFlow(): Flow<BotConfigEntity?> = botConfigDao.getActiveConfigFlow()

    suspend fun saveBotConfig(name: String, token: String): Long {
        val config = BotConfigEntity(
            name = name,
            token = token,
            isActive = true
        )
        // Deactivate all other configs
        botConfigDao.deactivateAll()
        return botConfigDao.insert(config)
    }

    suspend fun updateBotConfig(config: BotConfigEntity) {
        botConfigDao.update(config)
    }

    suspend fun setActiveConfig(configId: Int) {
        botConfigDao.setActiveConfig(configId)
    }

    suspend fun updateOffset(configId: Int, offset: Long) {
        botConfigDao.updateOffset(configId, offset)
        lastOffset = offset
    }

    suspend fun updateBotInfo(configId: Int, username: String, botId: Long) {
        botConfigDao.updateBotInfo(configId, username, botId)
    }

    suspend fun deleteConfig(config: BotConfigEntity) {
        botConfigDao.delete(config)
    }

    fun hasValidToken(): Boolean {
        return botToken?.takeIf { it.isNotBlank() }?.contains(":") == true
    }

    fun validateToken(token: String): Boolean {
        // Telegram bot tokens have format: 123456789:ABC-DEF1234ghIkl-zyx57W2v1u123ew11
        val regex = Regex("^\\d+:[A-Za-z0-9_-]{35,}$")
        return regex.matches(token.trim())
    }
}
