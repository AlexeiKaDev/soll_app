package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStabilizationGuardTest {
    @Test
    fun `strings xml keeps Russian user visible legacy labels`() {
        val strings = projectFile("app/src/main/res/values/strings.xml").readText()
        val forbidden = listOf(
            "Bot Status",
            "Start Bot",
            "Stop Bot",
            "Please set bot token first",
            "Network error",
            "Show welcome message",
            "Get device status",
        )

        forbidden.forEach { phrase ->
            assertFalse("Forbidden English phrase remains: $phrase", strings.contains(phrase))
        }
    }

    @Test
    fun `user visible fallback text stays Russian`() {
        val files = listOf(
            "app/src/main/java/com/soll/domain/command/handlers/PingHandler.kt",
            "app/src/main/java/com/soll/domain/command/CommandProcessor.kt",
            "app/src/main/java/com/soll/data/repository/BookRepository.kt",
            "app/src/main/java/com/soll/domain/epub/EpubParser.kt",
            "app/src/main/java/com/soll/domain/tts/TextToSpeechManager.kt",
            "app/src/main/java/com/soll/presentation/screens/tools/ToolsScreen.kt",
            "app/src/main/java/com/soll/presentation/screens/tools/bookreader/BookReaderScreen.kt",
            "app/src/main/java/com/soll/presentation/screens/tools/bookreader/BookReaderViewModel.kt",
        )
        val forbidden = listOf(
            "Pong!",
            "Response time",
            "Check if bot is alive",
            "Unknown error",
            "Failed to send message",
            "Failed to parse EPUB file",
            "Unknown Title",
            "TTS initialization failed",
            "Push-to-talk",
            "offline TTS",
            "System TTS",
            "pack-и",
            "pack-ов",
            "ToolJob ",
            "Telegram handler",
        )

        files.forEach { path ->
            val source = projectFile(path).readText()
            forbidden.forEach { phrase ->
                assertFalse("Forbidden user-visible English phrase remains in $path: $phrase", source.contains(phrase))
            }
        }
    }

    @Test
    fun `database does not use destructive fallback`() {
        val appModule = projectFile("app/src/main/java/com/soll/di/AppModule.kt").readText()
        val schema20 = projectFile("app/schemas/com.soll.data.local.SollDatabase/20.json").readText()
        val schema21 = projectFile("app/schemas/com.soll.data.local.SollDatabase/21.json").readText()
        val schema22 = projectFile("app/schemas/com.soll.data.local.SollDatabase/22.json").readText()

        assertFalse(appModule.contains("fallbackToDestructiveMigration()"))
        assertTrue(appModule.contains("migration1To2"))
        assertTrue(appModule.contains("migration3To4"))
        assertTrue(appModule.contains("migration19To20"))
        assertTrue(appModule.contains("migration20To21"))
        assertTrue(appModule.contains("migration21To22"))
        assertTrue(schema20.contains("\"version\": 20"))
        assertTrue(schema21.contains("\"version\": 21"))
        assertTrue(schema22.contains("\"version\": 22"))
        listOf(
            "approval_id",
            "tool_job_id",
            "execution_state",
            "outcome_artifacts_json",
            "value_metric",
            "branch",
            "pair_id",
        ).forEach { column ->
            assertTrue(appModule.contains("ADD COLUMN `$column`"))
            assertTrue(schema20.contains("\"columnName\": \"$column\""))
        }
        assertTrue(appModule.contains("DEFAULT '[]'"))
        assertTrue(appModule.contains("DEFAULT 'innovation'"))
        assertTrue(appModule.contains("ADD COLUMN `dedupe_key`"))
        assertTrue(schema21.contains("\"columnName\": \"dedupe_key\""))
        assertTrue(schema21.contains("\"name\": \"index_app_notifications_dedupe_key\""))
        listOf(
            "assigned_node_id",
            "required_capabilities_json",
            "routing_state",
        ).forEach { column ->
            assertTrue(appModule.contains("ADD COLUMN `$column`"))
            assertTrue(schema22.contains("\"columnName\": \"$column\""))
        }
    }

    @Test
    fun `manifest keeps release safer defaults`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"\${usesCleartextTraffic}\""))
        assertTrue(manifest.contains("android:launchMode=\"singleTop\""))
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android:scheme=\"soll\""))
        assertTrue(manifest.contains("android:host=\"pair\""))
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
        assertFalse(manifest.contains(".data.service.BotService"))
    }

    @Test
    fun `soll chat replaces active android telegram bot entry points`() {
        val destinations = projectFile("app/src/main/java/com/soll/presentation/navigation/AppDestinations.kt").readText()
        val navigation = projectFile("app/src/main/java/com/soll/presentation/navigation/AppNavigation.kt").readText()
        val launchTargets = projectFile("app/src/main/java/com/soll/presentation/navigation/AppLaunchTargets.kt").readText()
        val settings = projectFile("app/src/main/java/com/soll/presentation/screens/settings/SettingsScreen.kt").readText()
        val application = projectFile("app/src/main/java/com/soll/SollApplication.kt").readText()
        val channels = projectFile("app/src/main/java/com/soll/data/notification/SollNotificationChannels.kt").readText()
        val proactive = projectFile("app/src/main/java/com/soll/domain/assistant/proactive/ProactiveSuggestions.kt").readText()
        val settingsRepository = projectFile("app/src/main/java/com/soll/data/repository/SettingsRepository.kt").readText()
        val sollRepository = projectFile("app/src/main/java/com/soll/data/repository/SollRepository.kt").readText()

        assertTrue(projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatScreen.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatViewModel.kt").exists())
        assertTrue(destinations.contains("val bottomBar = listOf(Chat, Tasks, Tools, Settings)"))
        assertTrue(navigation.contains("startDestination = AppDestinations.Chat.route"))
        assertTrue(navigation.contains("navigateBottomBarRoute(screen.route)"))
        assertTrue(navigation.contains("popBackStack(AppDestinations.Chat.route, false)"))
        assertTrue(navigation.contains("ChatScreen("))
        assertTrue(launchTargets.contains("SECTION_CHAT"))
        assertTrue(application.contains("NOTIFICATION_CHANNEL_ID = \"soll_chat\""))
        assertTrue(channels.contains("CHAT_NOTIFICATION_ID"))
        assertTrue(channels.contains("Чат Soll"))
        assertTrue(settingsRepository.contains("RECOMMENDED_SOLL_SERVER_URL = \"https://sales.monolith-ost.com/\""))
        assertTrue(settingsRepository.contains("RECOMMENDED_SOLL_API_PATH_PREFIX = \"api/v1/soll\""))
        assertTrue(settingsRepository.contains("seedRecommendedSollEndpoint"))
        assertFalse(settingsRepository.contains("ifBlank { RECOMMENDED_SOLL_SERVER_URL }"))
        assertFalse(settingsRepository.contains("ifBlank { RECOMMENDED_SOLL_API_PATH_PREFIX }"))
        assertTrue(sollRepository.contains("rewriteSollApiUrl"))
        assertTrue(settings.contains("API путь"))
        assertTrue(settings.contains("Подставить рекомендуемый адрес"))
        assertFalse(settings.contains("Токен Telegram-бота"))
        assertFalse(settings.contains("@BotFather"))
        assertFalse(settings.contains("Автоматически запускать сервис бота"))
        assertFalse(settings.contains("команды бота"))
        assertFalse(settings.contains("Старый бот"))
        assertFalse(channels.contains("Архивный бот Soll"))
        assertTrue(channels.contains("Музыка Soll"))
        assertTrue(channels.contains("Читалка Soll"))
        assertFalse(channels.contains("Чтение книг"))
        assertFalse(proactive.contains("Настроить " + "Telegram"))
        assertFalse(proactive.contains("Запустить фонового " + "бота"))
        assertFalse(proactive.contains("Бот " + "не сможет принимать " + "команды"))
    }

    @Test
    fun `voice input waits for manual stop in chat and voice screens`() {
        val chatViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatViewModel.kt").readText()
        val voiceViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/voice/VoiceViewModel.kt").readText()
        val sttAdapter = projectFile("app/src/main/java/com/soll/data/voice/AndroidSpeechRecognizerAdapter.kt").readText()

        assertTrue(chatViewModel.contains("holdUntilStop = true"))
        assertTrue(voiceViewModel.contains("holdUntilStop = true"))
        assertFalse(voiceViewModel.contains(".cancelled()"))
        assertTrue(sttAdapter.contains("EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L"))
        assertTrue(sttAdapter.contains("EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L"))
        assertTrue(sttAdapter.contains("EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30_000L"))
    }

    @Test
    fun `chat history pagination stays explicit to avoid runaway large chat loads`() {
        val chatScreen = projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatScreen.kt").readText().normalizeLineEndings()
        val chatViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatViewModel.kt").readText().normalizeLineEndings()

        assertTrue(chatScreen.contains("HistoryLoader("))
        assertTrue(chatScreen.contains("onLoad = viewModel::loadOlderMessages"))
        assertTrue(chatScreen.contains("Загрузить историю"))
        assertTrue(chatViewModel.contains("beforeId = oldestId"))
        assertTrue(chatViewModel.contains("private const val CHAT_PAGE_SIZE = 80"))
        assertTrue(chatViewModel.contains("val scrollToBottomReason: ChatScrollReason = ChatScrollReason.NONE"))
        assertTrue(chatScreen.contains("visibleChatMessages(uiState.messages, uiState.searchQuery)"))
        assertTrue(chatScreen.contains("if (searchQuery.isBlank()) return messages"))
        assertTrue(chatViewModel.contains("metadata.matchesChatMetadataQuery(needle)"))
        assertFalse(chatViewModel.substringAfter("internal fun SollChatMessage.matchesChatQuery").substringBefore("internal fun SollChatMessage.isDisplayableChatMessage").contains("joinToString"))
        assertTrue(chatScreen.contains("shouldAutoScrollChatList("))
        assertTrue(chatScreen.contains("viewModel.onScrollRequestHandled(token)"))
        assertTrue(chatScreen.contains("contentWindowInsets = WindowInsets(0, 0, 0, 0)"))
        assertFalse(chatScreen.contains(".imePadding()"))
        assertTrue(chatViewModel.contains("private var refreshInFlight = false"))
        assertTrue(chatViewModel.contains("if (refreshInFlight) return@launch"))
        assertTrue(chatViewModel.contains("finally {\n                refreshInFlight = false"))
        assertTrue(chatViewModel.contains("fun refresh(showLoading: Boolean = true, afterIdOverride: Long? = null)"))
        assertTrue(chatViewModel.contains("val previousLastId = _uiState.value.messages.maxOfOrNull { it.id }"))
        assertTrue(chatViewModel.contains("runAssistant = true"))
        assertTrue(chatViewModel.contains("refresh(showLoading = false, afterIdOverride = previousLastId)"))
        assertFalse(chatViewModel.contains("runAssistant = false"))
        assertFalse(chatScreen.contains("LaunchedEffect(\n        listState.firstVisibleItemIndex"))
    }

    @Test
    fun `assistant chat messages keep structured header body badges and status colors`() {
        val chatScreen = projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatScreen.kt").readText().normalizeLineEndings()
        val chatViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatViewModel.kt").readText().normalizeLineEndings()

        assertTrue(chatScreen.contains("AssistantMessageContent("))
        assertTrue(chatScreen.contains("messageTitle(message)"))
        assertTrue(chatScreen.contains("ChatBadgeRow(message)"))
        assertTrue(chatScreen.contains("message.linkPreviewOrNull()"))
        assertTrue(chatScreen.contains("LinkifiedChatText("))
        assertTrue(chatScreen.contains("LocalUriHandler.current"))
        assertTrue(chatScreen.contains("extractChatLinks"))
        assertTrue(chatScreen.contains("message.actionUis()"))
        assertTrue(chatScreen.contains("CompactChatActionRow("))
        assertTrue(chatScreen.contains("CompactChatActionButton("))
        assertTrue(chatScreen.contains("FlowRow("))
        assertTrue(chatScreen.contains("private val ChatActionGreen = Color(0xFF247A52)"))
        assertTrue(chatScreen.contains("ButtonDefaults.buttonColors("))
        assertTrue(chatScreen.contains("containerColor = ChatActionGreen"))
        assertTrue(chatScreen.contains("contentColor = Color.White"))
        assertTrue(chatScreen.contains("Modifier\n            .defaultMinSize(minWidth = 0.dp, minHeight = 32.dp)"))
        assertTrue(chatScreen.contains("MaterialTheme.typography.labelSmall"))
        assertTrue(chatScreen.contains("textAlign = TextAlign.Center"))
        assertTrue(chatScreen.contains("ChatActionFeedbackBanner(text = feedback)"))
        assertTrue(chatScreen.contains("busyActionId = uiState.actionInFlightId"))
        assertTrue(chatScreen.contains("isRunning = busyActionId == action.id"))
        assertFalse(
            chatScreen
                .substringAfter("private fun AssistantMessageContent")
                .substringBefore("private fun ChatBadgeRow")
                .contains("Surface(\n        onClick = onClick"),
        )
        assertTrue(chatViewModel.contains("val actionFeedback: String? = null"))
        assertTrue(chatViewModel.contains("val actionInFlightId: String? = null"))
        assertTrue(chatViewModel.contains("actionFeedback = \"Выполняю: \${action.label}\""))
        assertTrue(chatViewModel.contains("actionFeedback = \"Готово: \${action.label}\""))
        assertTrue(chatViewModel.contains("actionFeedback = \"Ошибка: \${action.label}\""))
        assertTrue(chatScreen.contains("internal enum class ChatBadgeKind"))
        assertTrue(chatScreen.contains("ChatBadgeKind.STATUS"))
        assertTrue(chatScreen.contains("ChatBadgeKind.SECURITY"))
        assertTrue(chatScreen.contains("ChatBadgeKind.TASK"))
        assertTrue(chatScreen.contains("ChatBadgeKind.SOURCE"))
        assertTrue(chatScreen.contains("ChatBadgeKind.WARNING"))
        assertTrue(chatScreen.contains("metadata[\"badges\"].asBadgeMaps()"))
        assertTrue(chatScreen.contains("MaterialTheme.colorScheme.errorContainer"))
        assertTrue(chatScreen.contains("MaterialTheme.colorScheme.primaryContainer"))
        assertTrue(chatScreen.contains("MaterialTheme.colorScheme.tertiaryContainer"))
    }

    @Test
    fun `chat header keeps the Soll robot identity icon`() {
        val chatScreen = projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatScreen.kt").readText()

        assertTrue(chatScreen.contains("R.drawable.ic_ai_robot_notification"))
        assertTrue(chatScreen.contains("painterResource(R.drawable.ic_ai_robot_notification)"))
    }

    @Test
    fun `android system notifications use default color and source specific small icons`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val colors = projectFile("app/src/main/res/values/colors.xml").readText()
        val ttsService = projectFile("app/src/main/java/com/soll/data/service/TtsService.kt").readText()
        val musicService = projectFile("app/src/main/java/com/soll/data/service/MusicPlaybackService.kt").readText()
        val activityService = projectFile("app/src/main/java/com/soll/data/service/ActivityTrackingService.kt").readText()
        val notificationRepository = projectFile("app/src/main/java/com/soll/data/repository/SollNotificationRepository.kt").readText()
        val serverSyncService = projectFile("app/src/main/java/com/soll/data/service/SollServerSyncForegroundService.kt").readText()

        assertTrue(manifest.contains("com.google.firebase.messaging.default_notification_icon"))
        assertTrue(manifest.contains("@drawable/ic_ai_robot_notification"))
        assertFalse(manifest.contains("com.google.firebase.messaging.default_notification_color"))
        assertFalse(colors.contains("notification_icon_tint"))
        listOf(ttsService, musicService, activityService, serverSyncService).forEach { source ->
            assertTrue(source.contains("ic_soll_notification"))
            assertFalse(source.contains("ic_ai_robot_notification"))
            assertFalse(source.contains("R.drawable.ic_notification"))
            assertFalse(source.contains(".setColor("))
        }
        assertTrue(notificationRepository.contains("private fun notificationSmallIcon(request: SollNotificationRequest): Int"))
        assertTrue(notificationRepository.contains("request.source.equals(\"fcm\", ignoreCase = true)"))
        assertTrue(notificationRepository.contains("R.drawable.ic_ai_robot_notification"))
        assertTrue(notificationRepository.contains("R.drawable.ic_soll_notification"))
        assertFalse(notificationRepository.contains(".setColor("))
    }

    @Test
    fun `notification settings keep noisy sync events out of android shade by default`() {
        val preferences = projectFile("app/src/main/java/com/soll/data/notification/SystemNotificationPreferences.kt").readText()
        val syncWorker = projectFile("app/src/main/java/com/soll/data/repository/SollServerSyncWorker.kt").readText()
        val fcmService = projectFile("app/src/main/java/com/soll/data/service/SollFirebaseMessagingService.kt").readText()
        val pushRegistrar = projectFile("app/src/main/java/com/soll/data/service/AndroidPushTokenRegistrar.kt").readText()
        val application = projectFile("app/src/main/java/com/soll/SollApplication.kt").readText()
        val settingsRepository = projectFile("app/src/main/java/com/soll/data/repository/SettingsRepository.kt").readText()
        val notificationRepository = projectFile("app/src/main/java/com/soll/data/repository/SollNotificationRepository.kt").readText()
        val notificationDao = projectFile("app/src/main/java/com/soll/data/local/dao/AppNotificationDao.kt").readText()
        val grouping = projectFile("app/src/main/java/com/soll/data/notification/SystemNotificationGrouping.kt").readText()
        val settingsScreen = projectFile("app/src/main/java/com/soll/presentation/screens/settings/SettingsScreen.kt").readText()
        val settingsViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/settings/SettingsViewModel.kt").readText()
        val devicesViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/devices/DevicesViewModel.kt").readText()
        val mainActivity = projectFile("app/src/main/java/com/soll/presentation/MainActivity.kt").readText()
        val destinations = projectFile("app/src/main/java/com/soll/presentation/navigation/AppDestinations.kt").readText()
        val launchTargets = projectFile("app/src/main/java/com/soll/presentation/navigation/AppLaunchTargets.kt").readText()
        val navigation = projectFile("app/src/main/java/com/soll/presentation/navigation/AppNavigation.kt").readText()
        val parser = projectFile("app/src/main/java/com/soll/domain/soll/SollPairingPayloadParser.kt").readText()
        val scannerScreen = projectFile("app/src/main/java/com/soll/presentation/screens/tools/scanner/ScannerScreen.kt").readText()
        val scannerViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/tools/scanner/ScannerViewModel.kt").readText()

        assertTrue(preferences.contains("val DEFAULT_ALLOWED_CHANNELS = setOf("))
        assertTrue(preferences.contains("SollNotificationChannel.CHAT"))
        assertTrue(preferences.contains("SollNotificationChannel.ALERTS"))
        assertFalse(preferences.substringAfter("val DEFAULT_ALLOWED_CHANNELS = setOf(").substringBefore(")").contains("TOOL_JOBS"))
        assertFalse(preferences.substringAfter("val DEFAULT_ALLOWED_CHANNELS = setOf(").substringBefore(")").contains("EVENTS"))
        assertFalse(preferences.substringAfter("val DEFAULT_ALLOWED_CHANNELS = setOf(").substringBefore(")").contains("SERVER_SYNC"))
        assertTrue(syncWorker.contains("channel = SollNotificationChannel.SERVER_SYNC"))
        assertTrue(syncWorker.contains("priority = SollNotificationPriority.LOW"))
        assertTrue(fcmService.contains("classifyFcmNotification(data)"))
        assertTrue(fcmService.contains("hint.anyToken(\"task_board\", \"board\", \"sync\", \"poll\", \"heartbeat\") -> SollNotificationChannel.SERVER_SYNC"))
        assertTrue(fcmService.contains("SollNotificationChannel.EVENTS,"))
        assertTrue(fcmService.contains("SollNotificationChannel.TOOL_JOBS,"))
        assertTrue(fcmService.contains("SollNotificationChannel.SERVER_SYNC -> SollNotificationPriority.LOW"))
        assertTrue(fcmService.contains("systemGroupKey = fcmNotificationGroupKey(data)"))
        assertTrue(fcmService.contains("data[\"notification_group\"].nonBlank()"))
        assertTrue(fcmService.contains("runBlocking(Dispatchers.IO)"))
        assertFalse(fcmService.contains("serviceScope.launch"))
        assertFalse(fcmService.contains("serviceScope.cancel()"))
        val fcmReceiveBlock = fcmService.substringAfter("runBlocking(Dispatchers.IO)").substringBefore("}.onFailure")
        assertTrue(fcmReceiveBlock.indexOf("notificationCenter().post") < fcmReceiveBlock.indexOf("advanceSollChatLastSeenMessageId"))
        assertTrue(fcmReceiveBlock.indexOf("notificationCenter().post") < fcmReceiveBlock.indexOf("SollServerSyncScheduler.schedule"))
        assertTrue(application.contains("AndroidPushTokenRegistrar.registerCurrentToken("))
        assertTrue(application.contains("reason = \"startup\""))
        assertTrue(application.contains("force = true"))
        assertTrue(syncWorker.contains("shouldRecoverAndroidPushRegistration(status)"))
        assertTrue(syncWorker.contains("reason = \"server_token_count_zero\""))
        assertTrue(fcmService.contains("AndroidPushTokenRegistrar.registerToken(applicationContext, token, reason = \"fcm_refresh\")"))
        assertTrue(pushRegistrar.contains("FirebaseMessaging.getInstance()"))
        assertTrue(pushRegistrar.contains("PUSH_AUTH_MISSING_ERROR"))
        assertTrue(pushRegistrar.contains("hasSollPushRegistrationAuth(settings)"))
        assertTrue(pushRegistrar.contains("entryPoint.sollGateway().registerAndroidPushToken(cleanToken, provider = \"fcm\")"))
        assertTrue(pushRegistrar.contains("if (!force && !settings.shouldRegisterSollPushToken(cleanToken))"))
        val repository = projectFile("app/src/main/java/com/soll/data/repository/SollRepository.kt").readText()
        assertTrue(repository.contains("private suspend fun ensureDeviceAuthorizationHeader()"))
        assertTrue(repository.contains("service().getAndroidSyncStatus(ensureDeviceAuthorizationHeader() ?: readAuthorizationHeader())"))
        assertTrue(repository.contains("val authorization = ensureDeviceAuthorizationHeader() ?: readAuthorizationHeader()"))
        assertTrue(repository.contains("service().refreshDeviceToken(current)"))
        assertTrue(repository.contains("issueDeviceToken(deviceId, pairingSecret)"))
        assertTrue(repository.contains("DEVICE_TOKEN_REFRESH_SAFETY_MS"))
        assertTrue(repository.contains("parseIsoInstantMillis"))
        assertTrue(repository.contains("androidPush = SollAndroidPushHealth("))
        assertTrue(settingsRepository.contains("fun shouldRegisterSollPushToken(token: String, nowMillis: Long = System.currentTimeMillis())"))
        assertTrue(settingsRepository.contains("return nowMillis - sollPushTokenRegisteredAt > DAY_MS"))
        assertTrue(settingsScreen.contains("Push FCM"))
        assertTrue(settingsScreen.contains("pushTokenStatusText(pushTokenRegisteredAt, pushTokenLastError)"))
        assertTrue(settingsScreen.contains("isRetryingPushToken = uiState.isRetryingSollPushToken"))
        assertTrue(settingsScreen.contains("Text(if (isRetryingPushToken) \"Проверяю\" else \"Повторить\")"))
        assertTrue(settingsViewModel.contains("fun retryAndroidPushTokenRegistration()"))
        assertTrue(settingsViewModel.contains("reason = \"settings_manual_retry\""))
        assertTrue(settingsViewModel.contains("reason = \"settings_saved\""))
        assertTrue(settingsRepository.contains("fun applySollPairingPayload(payload: SollPairingPayload)"))
        assertTrue(settingsScreen.contains("onScanSollPairingQr"))
        assertTrue(settingsScreen.contains("Сканировать QR"))
        assertFalse(settingsViewModel.contains("fun applySollPairingCode()"))
        assertFalse(settingsViewModel.contains("reason = \"settings_qr_pairing\""))
        assertFalse(settingsScreen.contains("QR / pairing code"))
        assertFalse(settingsScreen.contains("Применить QR"))
        assertTrue(mainActivity.contains("SollPairingPayloadParser::parse"))
        assertTrue(mainActivity.contains("reason = \"deep_link_pairing\""))
        assertTrue(mainActivity.contains("ActivityResultContracts.RequestPermission()"))
        assertTrue(mainActivity.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(mainActivity.contains("reason = \"notification_permission_granted\""))
        assertTrue(destinations.contains("const val SCANNER = \"scanner\""))
        assertTrue(launchTargets.contains("SECTION_SETTINGS"))
        assertTrue(navigation.contains("AppDestinations.Settings.route"))
        assertTrue(navigation.contains("ScannerScreen("))
        assertTrue(navigation.contains("autoStartCamera = true"))
        assertTrue(navigation.contains("pairingMode = true"))
        assertTrue(scannerScreen.contains("autoStartCamera: Boolean = false"))
        assertTrue(scannerScreen.contains("pairingMode: Boolean = false"))
        assertTrue(scannerScreen.contains("if (pairingMode) \"QR pairing\" else \"Сканер\""))
        assertTrue(scannerScreen.contains("PairingCameraPanel("))
        assertFalse(scannerScreen.contains("return@Column"))
        assertTrue(scannerScreen.contains("requireScannerCapability = false"))
        assertTrue(scannerScreen.contains("qrOnly = true"))
        assertTrue(scannerScreen.contains("qrBarcodeScannerOptions()"))
        assertTrue(scannerScreen.contains("viewModel.handleCameraBarcode(rawValue, format, pairingOnly = true)"))
        assertTrue(scannerScreen.contains("modifier = Modifier.fillMaxSize()"))
        assertTrue(scannerViewModel.contains("pairingOnly: Boolean = false"))
        assertTrue(scannerViewModel.contains("handlePairingCameraBarcode(rawValue)"))
        assertTrue(scannerViewModel.contains("QR pairing найден"))
        assertTrue(scannerViewModel.contains("!pairingOnly && !ensureScannerCapability()"))
        assertTrue(scannerViewModel.contains("requireScannerCapability: Boolean = true"))
        assertTrue(scannerViewModel.contains("if (pairingOnly)"))
        assertTrue(scannerViewModel.contains("Это не QR pairing Soll"))
        assertTrue(scannerViewModel.contains("SollPairingPayloadParser.parse(result.value)"))
        assertTrue(scannerViewModel.contains("reason = \"scanner_qr_pairing\""))
        assertTrue(scannerViewModel.contains("settingsRepository.applySollPairingPayload(payload)"))
        assertTrue(parser.contains("soll_android_pairing"))
        assertTrue(settingsViewModel.contains("force = true"))
        assertTrue(devicesViewModel.contains("reason = \"device_token_issued\""))
        assertTrue(devicesViewModel.contains("reason = \"device_token_refreshed\""))
        assertTrue(settingsScreen.contains("По умолчанию в Android идут только чат и важное"))
        assertTrue(settingsScreen.contains("Технические события фоновой синхронизации; выключено по умолчанию."))
        assertTrue(notificationRepository.contains(".setGroup(groupKey)"))
        assertTrue(notificationRepository.contains(".setGroupSummary(true)"))
        assertTrue(notificationRepository.contains("SollNotificationChannel.CHAT,"))
        assertTrue(notificationRepository.contains("SollNotificationChannel.ALERTS -> NotificationCompat.GROUP_ALERT_CHILDREN"))
        assertTrue(notificationRepository.contains("NotificationCompat.GROUP_ALERT_SUMMARY"))
        assertTrue(notificationRepository.contains("systemNotificationSummaryId(request.channel, groupKey)"))
        assertTrue(notificationDao.contains("getUnreadCountForChannel"))
        assertTrue(grouping.contains("systemNotificationGroupKey"))
        assertTrue(grouping.contains("rawGroup: String? = null"))
        assertTrue(grouping.contains("systemNotificationSummaryText"))
        assertTrue(grouping.contains("systemNotificationSummaryId"))
    }

    @Test
    fun `task workspace keeps roadmap and source mutation controls`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val appNavigation = projectFile("app/src/main/java/com/soll/presentation/navigation/AppNavigation.kt").readText().normalizeLineEndings()
        val screen = projectFile("app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardScreen.kt").readText().normalizeLineEndings()
        val todoScreen = projectFile("app/src/main/java/com/soll/presentation/screens/todo/DailyTodoScreen.kt").readText().normalizeLineEndings()
        val todoViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/todo/DailyTodoViewModel.kt").readText().normalizeLineEndings()
        val viewModel = projectFile("app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardViewModel.kt").readText().normalizeLineEndings()
        val api = projectFile("app/src/main/java/com/soll/data/api/SollApiService.kt").readText()
        val repository = projectFile("app/src/main/java/com/soll/data/repository/SollRepository.kt").readText()
        val destinations = projectFile("app/src/main/java/com/soll/presentation/navigation/AppDestinations.kt").readText()

        assertTrue(screen.contains("TaskWorkspaceMode.TASKS"))
        assertTrue(screen.contains("TaskWorkspaceMode.INSIGHTS"))
        assertTrue(screen.contains("TaskWorkspaceMode.ROADMAP -> RoadmapMode"))
        assertTrue(screen.contains("TaskWorkspaceMode.SOURCES -> SourcesMode"))
        assertFalse(screen.contains("TaskWorkspaceMode.GRAPH"))
        assertFalse(destinations.contains("title = \"Граф\""))
        assertTrue(viewModel.contains("fun addRoadmapLine"))
        assertTrue(viewModel.contains("fun deleteRoadmapLine"))
        assertTrue(viewModel.contains("fun createSource"))
        assertTrue(viewModel.contains("fun deleteSource"))
        assertTrue(viewModel.contains("fun checkSource"))
        assertTrue(viewModel.contains("sourceItemsCache.keys.retainAll(sourceIds)"))
        assertTrue(viewModel.contains("sourceId?.takeIf { it in sourceIds }"))
        val sourceFailureBlock = viewModel
            .substringAfter("private fun loadSourceItems")
            .substringAfter("getOrElse { error ->")
            .substringBefore("return@launch")
        assertTrue(sourceFailureBlock.contains("if (it.selectedSourceId == sourceId)"))
        assertTrue(api.contains("@GET(\"api/v1/roadmap\")"))
        assertTrue(api.contains("@POST(\"api/v1/roadmap/stages/{stage_id}/lines\")"))
        assertTrue(api.contains("@DELETE(\"api/v1/roadmap/stages/{stage_id}/lines/{line}\")"))
        assertTrue(api.contains("@POST(\"api/v1/roadmap/stages/{stage_id}/lines/{line}/task\")"))
        assertTrue(api.contains("@GET(\"api/v1/sources\")"))
        assertTrue(api.contains("data class AndroidWorkspaceSyncResponse"))
        assertTrue(api.contains("val workspace: AndroidWorkspaceSyncResponse = AndroidWorkspaceSyncResponse()"))
        assertTrue(api.contains("sourceItemsBySource: Map<String, List<SourceItemResponse>>"))
        assertTrue(api.contains("@POST(\"api/v1/sources\")"))
        assertTrue(api.contains("@DELETE(\"api/v1/sources/{source_id}\")"))
        assertTrue(api.contains("@POST(\"api/v1/sources/{source_id}/items/{item_id}/task\")"))
        assertTrue(api.contains("@POST(\"api/v1/android/location\")"))
        assertTrue(api.contains("AndroidLocationUpdateRequest"))
        assertTrue(api.contains("@GET(\"api/v1/daily/tasks/today\")"))
        assertTrue(api.contains("@POST(\"api/v1/daily/tasks/today\")"))
        assertTrue(api.contains("@PATCH(\"api/v1/daily/tasks/today/{task_id}\")"))
        assertTrue(api.contains("@GET(\"api/v1/daily/tasks/today/{task_id}/detail\")"))
        assertTrue(api.contains("@POST(\"api/v1/daily/tasks/today/{task_id}/research\")"))
        assertTrue(api.contains("@POST(\"api/v1/daily/tasks/today/{task_id}/attachments\")"))
        assertTrue(destinations.contains("title = \"Список дел\""))
        assertTrue(destinations.contains("const val DAILY_TODO = \"daily_todo\""))
        assertTrue(destinations.contains("route = Routes.DAILY_TODO"))
        assertFalse(destinations.contains("route = Tasks.route"))
        assertFalse(destinations.contains("Геопозиция для поиска"))
        assertTrue(manifest.contains(".presentation.screens.todo.DailyTodoActivity"))
        assertTrue(appNavigation.contains("context.startActivity(Intent(context, DailyTodoActivity::class.java))"))
        assertFalse(appNavigation.contains("composable(Routes.DAILY_TODO)"))
        assertFalse(appNavigation.contains("TaskWorkspaceMode.DAILY"))
        assertTrue(viewModel.contains("val selectedMode: TaskWorkspaceMode = TaskWorkspaceMode.TASKS"))
        assertTrue(screen.contains("initialMode: TaskWorkspaceMode = TaskWorkspaceMode.TASKS"))
        assertTrue(screen.contains("visibleModes: List<TaskWorkspaceMode> = listOf("))
        assertFalse(screen.contains("TaskWorkspaceMode.DAILY"))
        assertFalse(screen.contains("DailyTasksMode"))
        assertFalse(screen.contains("DailyTaskAddCard"))
        assertFalse(screen.contains("DailyTaskRow"))
        assertFalse(screen.contains("SollDailyTask"))
        assertFalse(viewModel.contains("TaskWorkspaceMode.DAILY"))
        assertFalse(viewModel.contains("FieldMapRepository"))
        assertFalse(viewModel.contains("getTodayDailyTasks"))
        assertFalse(viewModel.contains("addTodayDailyTask"))
        assertFalse(viewModel.contains("updateTodayDailyTask"))
        assertFalse(viewModel.contains("uploadTodayDailyTaskAttachment"))
        assertFalse(viewModel.contains("dailyTasks"))
        assertTrue(todoScreen.contains("fun DailyTodoScreen("))
        assertTrue(todoScreen.contains("DailyTodoAddCard("))
        assertTrue(todoScreen.contains("addAttachmentPicker.launch(\"*/*\")"))
        assertTrue(todoScreen.contains("existingAttachmentPicker.launch(\"*/*\")"))
        assertTrue(todoScreen.contains("ActivityResultContracts.TakePicture()"))
        assertTrue(todoScreen.contains("FileProvider.getUriForFile"))
        assertTrue(todoScreen.contains("DailyTodoTabs("))
        assertTrue(todoScreen.contains("DailyTodoTab.SOURCES"))
        assertTrue(todoScreen.contains("DailyTodoDetailPane("))
        assertTrue(todoScreen.contains("DailyTodoSourcesMode("))
        assertTrue(todoScreen.contains("Text(\"Источники дел\")"))
        assertTrue(todoScreen.contains("Text(\"Найти\")"))
        assertTrue(todoScreen.contains("Icons.Default.AttachFile"))
        assertTrue(todoScreen.contains("Icons.Default.PhotoCamera"))
        assertTrue(todoScreen.contains("Icons.Default.Delete"))
        assertTrue(todoScreen.contains("contentDescription = \"Удалить\""))
        assertTrue(todoScreen.contains("IconButton(onClick = onDelete"))
        assertTrue(todoScreen.contains("combinedClickable("))
        assertTrue(todoScreen.contains("onLongClick = if (isRunning) null else onDelete"))
        assertTrue(todoScreen.contains("onDeleteTask = viewModel::deleteTask"))
        assertFalse(todoScreen.contains("requestDeleteTask"))
        assertFalse(todoScreen.contains("Удалить дело?"))
        assertFalse(todoScreen.contains("SnackbarResult"))
        assertFalse(todoScreen.contains("Text(\"Файл\")"))
        assertFalse(todoScreen.contains("Text(\"Фото\")"))
        assertTrue(todoScreen.contains("ActivityResultContracts.RequestMultiplePermissions()"))
        assertTrue(todoScreen.contains("Manifest.permission.ACCESS_FINE_LOCATION"))
        assertFalse(todoScreen.contains("TaskSummary"))
        assertFalse(todoScreen.contains("TaskWorkspaceTabs"))
        assertFalse(todoScreen.contains("TaskWorkspaceMode"))
        assertFalse(todoScreen.contains("С геопозицией"))
        assertTrue(todoViewModel.contains("class DailyTodoViewModel"))
        assertTrue(todoViewModel.contains("sollGateway.getTodayDailyTasks()"))
        assertTrue(todoViewModel.contains("fun openTask(task: SollDailyTask)"))
        assertTrue(todoViewModel.contains("getTaskDetailWithReferences(task)"))
        assertTrue(todoViewModel.contains("callDailyTaskReferences(task)"))
        assertTrue(todoViewModel.contains("addReference(\"task-${'$'}{taskIndex + 1}\")"))
        assertTrue(todoViewModel.contains("isDailyTaskReferenceError()"))
        assertTrue(todoViewModel.contains("deleteDailyTaskWithReferences(task)"))
        assertTrue(todoViewModel.contains("sollGateway.updateTodayDailyTask(taskRef, done = true)"))
        assertTrue(todoViewModel.contains("withoutCompletedDailyTasks()"))
        assertTrue(todoViewModel.contains("fun researchSelectedTask(publishLocation: Boolean = true)"))
        assertTrue(todoViewModel.contains("sollGateway.researchTodayDailyTask(taskRef)"))
        assertTrue(todoViewModel.contains("fun deleteTask(task: SollDailyTask)"))
        assertTrue(todoViewModel.contains("fun loadSources"))
        assertTrue(todoViewModel.contains("fun createSource(name: String, target: String, sourceType: String = \"web\")"))
        assertTrue(todoViewModel.contains("sollGateway.listSources(SollSourceScope.DAILY_TODO)"))
        assertTrue(todoViewModel.contains("sollGateway.createSource(name, target, SollSourceScope.DAILY_TODO, sourceType)"))
        assertFalse(todoViewModel.contains("sollGateway.listSources().fold"))
        assertTrue(todoViewModel.contains("fun checkSource(source: SollMonitoredSource)"))
        assertFalse(todoViewModel.contains("fun createTaskFromSourceItem"))
        assertFalse(todoScreen.contains("Text(\"В задачу\")"))
        assertTrue(todoViewModel.contains("createdList.createdTaskId"))
        assertTrue(todoViewModel.contains("sollGateway.uploadTodayDailyTaskAttachment(createdTaskId, attachmentUri)"))
        assertTrue(todoViewModel.contains("fieldMapRepository.publishCurrentLocationToSoll()"))
        assertTrue(api.contains("@Json(name = \"created_task_id\")"))
        assertTrue(repository.contains("uploadTodayDailyTaskAttachment("))
        assertTrue(repository.contains("getTodayDailyTaskDetail("))
        assertTrue(repository.contains("researchTodayDailyTask("))
        assertTrue(repository.contains("private suspend fun writeAuthorizationHeader()"))
        assertTrue(api.contains("@Query(\"import_daily\") importDaily: Boolean = false"))
        assertTrue(repository.contains("importDaily = false"))
        assertTrue(repository.contains(".withoutDailyTodoTasks()"))
        assertTrue(repository.contains(".filterForSourceScope(scope)"))
        assertTrue(repository.contains("filterNot { it.isDailyTodoOrigin() }"))
        assertTrue(repository.contains("mapNotNull { it.toDomainOrNull() }"))
        assertFalse(repository.contains("taskBoardToDailyTaskList("))
        assertFalse(repository.contains("force_task_intake"))
        assertFalse(repository.contains("taskIntakeTaskId("))
        assertTrue(api.contains("@Path(value = \"action_id\", encoded = true)"))
        assertTrue(api.contains("@Path(value = \"task_id\", encoded = true)"))
        assertTrue(viewModel.contains("fun createTaskFromSourceItem(sourceId: String, item: SollSourceItem)"))
        assertTrue(viewModel.contains("fun createTaskFromRoadmapLine(stageId: String, line: SollRoadmapLine)"))
        assertTrue(viewModel.contains("roadmapLineTaskKey(stageId, line.line)"))
        assertTrue(repository.contains("createTaskFromRoadmapLine("))
        assertTrue(repository.contains("createTaskFromSourceItem("))
        assertTrue(repository.contains("getAndroidSyncStatus().getOrThrow().insights"))
        assertTrue(repository.contains("getAndroidSyncStatus().getOrThrow().sources"))
        assertTrue(repository.contains("sources.filterForSourceScope(scope)"))
        assertTrue(api.contains("@Query(\"scope\") scope: String = \"project_soll\""))
        assertTrue(api.contains("val scope: String = \"project_soll\""))
        assertTrue(repository.contains("sourceItemsBySource[sourceId.trim()]"))
        assertTrue(repository.contains("private fun Throwable.isWorkspaceSnapshotFallbackStatus()"))
        assertTrue(repository.contains("publishAndroidLocation("))
        assertTrue(repository.contains("ensureDeviceAuthorizationHeader() ?: readAuthorizationHeader()"))
        assertTrue(repository.contains("private fun String.encodedSollPathSegment(fieldName: String): String"))
        assertTrue(repository.contains("actionId = cleanActionId.encodedSollPathSegment(fieldName = \"action_id\")"))
        assertTrue(repository.contains("taskId.encodedSollPathSegment(fieldName = \"task_id\")"))
        assertFalse(
            repository
                .substringAfter("override suspend fun executeChatAction")
                .substringBefore("override suspend fun issueDeviceToken")
                .contains("encryptedEnvelopeOrNull"),
        )
        assertTrue(screen.contains("Text(\"В задачу\")"))
        assertTrue(screen.contains("uiState.roadmapLineTaskKey == roadmapLineTaskKey(stage.id, line.line)"))
        assertTrue(screen.contains("items = stage.lines"))
        assertTrue(screen.contains("contentType = { \"roadmap-line\" }"))
        assertTrue(screen.contains("RoadmapLineCard"))
        assertTrue(screen.contains("RoadmapStageEditor"))
        assertFalse(screen.contains("private fun RoadmapStageCard("))
        assertFalse(screen.contains("private sealed interface RoadmapRow"))
        assertFalse(screen.contains("roadmapRows("))
        assertTrue(repository.contains("private const val TASK_BOARD_SECTION_LIMIT = 80"))
        assertTrue(repository.contains("private const val TASK_BOARD_MAX_SECTION_LIMIT = 500"))
        assertTrue(repository.contains("limitPerSection = sectionLimit"))
        assertTrue(viewModel.contains("private const val DEFAULT_TASK_BOARD_SECTION_LIMIT = 80"))
        assertTrue(viewModel.contains("private const val MAX_TASK_BOARD_SECTION_LIMIT = 500"))
        assertTrue(viewModel.contains("fun loadMoreTasks()"))
        assertTrue(viewModel.contains("sollGateway.getTaskBoard(limitPerSection = sectionLimit)"))
        assertTrue(screen.contains("LoadMoreTasksRow"))
        assertTrue(screen.contains("uiState.canLoadMoreTasks"))
        assertTrue(viewModel.contains("enum class InsightStatusFilter"))
        assertTrue(viewModel.contains("selectedInsightStatus.apiStatus"))
        assertTrue(screen.contains("InsightStatusFilters"))
        assertTrue(screen.contains("InsightStatusFilter.entries.forEach"))
        assertTrue(repository.contains("private val SOURCE_TYPES = setOf(SOURCE_TYPE_WEB, \"rss\", \"telegram_chat\")"))
        assertTrue(viewModel.contains("fun createSource(name: String, target: String, sourceType: String = \"web\")"))
        assertTrue(viewModel.contains("sollGateway.listSources(SollSourceScope.PROJECT_SOLL)"))
        assertTrue(viewModel.contains("sollGateway.createSource(name, target, SollSourceScope.PROJECT_SOLL, sourceType)"))
        assertFalse(viewModel.contains("sollGateway.listSources().fold"))
        assertTrue(screen.contains("private enum class SourceTypeOption"))
        assertTrue(screen.contains("TELEGRAM(\"Telegram\", \"telegram_chat\")"))
        assertTrue(viewModel.contains("internal fun SollTask.matchesTaskQuery(query: String)"))
        assertTrue(viewModel.contains("return filter { task -> task.matchesTaskQuery(needle) }"))
        assertFalse(viewModel.substringAfter("private fun List<SollTask>.filterByQuery").substringBefore("internal fun SollTask.matchesTaskQuery").contains("joinToString"))
        assertTrue(screen.contains("private const val TASK_DESCRIPTION_COLLAPSED_LINES = 4"))
        assertTrue(screen.contains("maxLines = if (expanded) Int.MAX_VALUE else TASK_DESCRIPTION_COLLAPSED_LINES"))
        assertTrue(screen.contains("text = \"Источник: \${task.sourceRef}\""))
        assertTrue(screen.contains("maxLines = 1"))
        assertTrue(screen.contains("uiState.routedOpenTaskCount"))
        assertTrue(screen.contains("task.hasRoutingContext()"))
        assertTrue(screen.contains("routingState.routingStateLabel()"))
        assertTrue(screen.contains("requiredCapabilities.requiredCapabilitiesLabel()"))
        assertTrue(viewModel.contains("internal fun SollTask.hasRoutingContext()"))
        assertTrue(viewModel.contains("assignedNodeId.orEmpty().contains"))
        assertTrue(viewModel.contains("requiredCapabilities.any"))
        assertTrue(screen.contains("key = { it.taskListKey() }"))
        assertTrue(screen.contains("val visibility = taskActionVisibility(status = status, taskId = taskId)"))
        assertTrue(screen.contains("internal fun taskActionVisibility(status: String, taskId: String): TaskActionVisibility"))
        assertTrue(screen.contains("internal fun SollTask.taskListKey(): String"))
        assertFalse(screen.substringAfter("private fun TaskActions(").substringBefore("@Composable\nprivate fun ErrorMessage").contains("setOf("))
    }

    @Test
    fun `task priority badges normalize ABCD and keep app palette colors`() {
        val screen = projectFile("app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardScreen.kt").readText()
        val viewModel = projectFile("app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardViewModel.kt").readText()

        assertTrue(screen.contains("priorityBadgeStyle(task.priority)"))
        assertTrue(screen.contains("private fun priorityLabel(priority: String)"))
        assertTrue(screen.contains("\"A\", \"P1\" -> \"A\""))
        assertTrue(screen.contains("\"B\", \"P2\" -> \"B\""))
        assertTrue(screen.contains("\"C\", \"P3\" -> \"C\""))
        assertTrue(screen.contains("\"D\", \"P4\" -> \"D\""))
        assertTrue(screen.contains("\"A\" -> Color(0xFF247A52)"))
        assertTrue(screen.contains("\"B\" -> MaterialTheme.colorScheme.primary"))
        assertTrue(screen.contains("\"C\" -> MaterialTheme.colorScheme.tertiary"))
        assertTrue(screen.contains("\"D\" -> MaterialTheme.colorScheme.outline"))
        assertTrue(viewModel.contains("it.priority.normalizedTaskPriorityLabel() == filter.label"))
        assertTrue(viewModel.contains("private fun String.normalizedTaskPriorityLabel()"))
        assertTrue(viewModel.contains("\"A\", \"P1\" -> \"A\""))
        assertTrue(viewModel.contains("\"D\", \"P4\" -> \"D\""))
    }

    @Test
    fun `tool widgets stay archived while media services are active`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val resources = listOf(
            "app/src/main/res/xml/widget_music_info.xml",
            "app/src/main/res/xml/widget_reader_info.xml",
            "app/src/main/res/xml/widget_notes_info.xml",
            "app/src/main/res/xml-v31/widget_music_info.xml",
            "app/src/main/res/xml-v31/widget_reader_info.xml",
            "app/src/main/res/xml-v31/widget_notes_info.xml",
            "app/src/main/res/layout/widget_music.xml",
            "app/src/main/res/layout/widget_reader.xml",
            "app/src/main/res/layout/widget_notes.xml",
        )

        assertFalse(manifest.contains(".presentation.widgets.MusicWidgetProvider"))
        assertFalse(manifest.contains(".presentation.widgets.ReaderWidgetProvider"))
        assertFalse(manifest.contains(".presentation.widgets.NotesWidgetProvider"))
        assertTrue(manifest.contains(".data.service.MusicPlaybackService"))
        assertTrue(manifest.contains(".data.service.TtsService"))
        assertTrue(manifest.contains("FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        resources.forEach { path ->
            assertTrue("Missing widget resource: $path", projectFile(path).exists())
        }
        assertTrue(projectFile("app/src/main/res/layout/widget_music.xml").readText().contains("@+id/widget_artwork"))
        assertTrue(projectFile("app/src/main/res/layout/widget_reader.xml").readText().contains("@+id/widget_artwork"))
        assertTrue(projectFile("app/src/main/res/xml/widget_music_info.xml").readText().contains("android:minHeight=\"40dp\""))
        assertTrue(projectFile("app/src/main/res/xml/widget_reader_info.xml").readText().contains("android:minHeight=\"40dp\""))
        assertTrue(projectFile("app/src/main/res/xml/widget_notes_info.xml").readText().contains("android:minHeight=\"40dp\""))
        assertFalse(projectFile("app/src/main/res/xml/widget_music_info.xml").readText().contains("android:targetCell"))
        assertFalse(projectFile("app/src/main/res/xml/widget_reader_info.xml").readText().contains("android:targetCell"))
        assertFalse(projectFile("app/src/main/res/xml/widget_notes_info.xml").readText().contains("android:targetCell"))
        assertTrue(projectFile("app/src/main/res/xml-v31/widget_music_info.xml").readText().contains("android:targetCellHeight=\"1\""))
        assertTrue(projectFile("app/src/main/res/xml-v31/widget_reader_info.xml").readText().contains("android:targetCellHeight=\"1\""))
        assertTrue(projectFile("app/src/main/res/xml-v31/widget_music_info.xml").readText().contains("android:targetCellWidth=\"3\""))
        assertTrue(projectFile("app/src/main/res/xml-v31/widget_reader_info.xml").readText().contains("android:targetCellWidth=\"3\""))
        assertTrue(projectFile("app/src/main/res/xml-v31/widget_notes_info.xml").readText().contains("android:targetCellWidth=\"2\""))
        val readerWidgetProvider = projectFile("app/src/main/java/com/soll/presentation/widgets/ReaderWidgetProvider.kt").readText()
        assertTrue(readerWidgetProvider.contains("ReaderWidgetStateStore.read"))
        assertFalse(readerWidgetProvider.contains("runBlocking"))
        assertTrue(projectFile("app/src/main/java/com/soll/presentation/widgets/ReaderWidgetStateStore.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/data/repository/BookRepository.kt").readText().contains("extractReaderWidgetExcerpt"))
    }

    @Test
    fun `theme switch keeps dark themes including aquik`() {
        val themeVariant = projectFile("app/src/main/java/com/soll/ui/theme/SollThemeVariant.kt").readText()
        val theme = projectFile("app/src/main/java/com/soll/ui/theme/Theme.kt").readText()
        val settings = projectFile("app/src/main/java/com/soll/presentation/screens/settings/SettingsScreen.kt").readText()
        val repository = projectFile("app/src/main/java/com/soll/data/repository/SettingsRepository.kt").readText()

        assertTrue(themeVariant.contains("SOLL"))
        assertTrue(themeVariant.contains("CLASSIC"))
        assertTrue(themeVariant.contains("AURORA"))
        assertTrue(themeVariant.contains("AQUIK"))
        assertTrue(theme.contains("SollLightColorScheme"))
        assertTrue(theme.contains("ClassicDarkColorScheme"))
        assertTrue(theme.contains("AuroraDarkColorScheme"))
        assertTrue(theme.contains("AquikDarkColorScheme"))
        assertTrue(repository.contains("\"soll\""))
        assertTrue(repository.contains("\"aquik\""))
        assertTrue(settings.contains("Тема"))
    }

    @Test
    fun `material typography uses bundled Carlsberg font`() {
        listOf(
            "app/src/main/res/font/carlsberg_sans_light.ttf",
            "app/src/main/res/font/carlsberg_sans_bold.ttf",
            "app/src/main/res/font/carlsberg_sans_black.ttf",
        ).forEach { path ->
            assertTrue("Missing Carlsberg font asset: $path", projectFile(path).exists())
        }

        val type = projectFile("app/src/main/java/com/soll/ui/theme/Type.kt").readText()
        val theme = projectFile("app/src/main/java/com/soll/ui/theme/Theme.kt").readText()

        assertTrue(type.contains("val CarlsbergSansFamily = FontFamily("))
        assertTrue(type.contains("Font(R.font.carlsberg_sans_light, FontWeight.Light)"))
        assertTrue(type.contains("Font(R.font.carlsberg_sans_bold, FontWeight.Bold)"))
        assertTrue(type.contains("Font(R.font.carlsberg_sans_black, FontWeight.Black)"))
        assertFalse(type.contains("FontFamily.Default"))
        assertTrue(
            "Every Material typography slot must use CarlsbergSansFamily",
            Regex("fontFamily\\s*=\\s*CarlsbergSansFamily").findAll(type).count() >= 15,
        )
        assertTrue(theme.contains("typography = Typography"))
    }

    @Test
    fun `launcher icon uses soll green background`() {
        val colors = projectFile("app/src/main/res/values/colors.xml").readText()
        val foreground = projectFile("app/src/main/res/drawable/ic_launcher_foreground.xml").readText()

        assertTrue(colors.contains("<color name=\"ic_launcher_background\">#247A52</color>"))
        assertFalse(colors.contains("#FF000000"))
        assertTrue(foreground.contains("android:fillColor=\"#FFFFFF\""))
    }

    @Test
    fun `gradle toolchain keeps processors and aar stripping explicit`() {
        val buildGradle = projectFile("app/build.gradle.kts").readText()

        assertTrue(buildGradle.contains("ksp(libs.moshi.codegen)"))
        assertTrue(buildGradle.contains("hiltJavaProcessors"))
        assertTrue(buildGradle.contains("StripOnnxRuntimeAarTask"))
        assertFalse(buildGradle.contains("onnxRuntimeAndroidBase.singleFile"))
        assertFalse(buildGradle.contains("zipTree(onnxRuntimeAndroidBase"))
        assertFalse(buildGradle.contains("while (true)"))
    }

    @Test
    fun `local ai model caches stay out of android asset paths`() {
        val assetRoots = listOfNotNull(
            optionalProjectFile("app/src/main/assets"),
            optionalProjectFile("app/assets"),
        )
        val modelExtensions = setOf(
            "onnx",
            "onnx_data",
            "gguf",
            "safetensors",
            "ckpt",
            "pt",
            "pth",
            "bin",
        )
        val offenders = assetRoots.flatMap { root ->
            root.walkTopDown()
                .filter { file -> file.isFile && file.extension.lowercase() in modelExtensions }
                .map { file -> file.invariantSeparatorsPath }
                .toList()
        }

        assertTrue("Local AI model caches must live under D:\\AI\\Models, not Android asset paths: $offenders", offenders.isEmpty())
    }

    @Test
    fun `device qa covers manual roadmap checks`() {
        val models = projectFile("app/src/main/java/com/soll/domain/deviceqa/DeviceQaModels.kt").readText()
        val repository = projectFile("app/src/main/java/com/soll/data/repository/DeviceQaRepository.kt").readText()
        val settings = projectFile("app/src/main/java/com/soll/presentation/screens/settings/SettingsScreen.kt").readText()
        val settingsViewModel = projectFile("app/src/main/java/com/soll/presentation/screens/settings/SettingsViewModel.kt").readText()
        val settingsRepository = projectFile("app/src/main/java/com/soll/data/repository/SettingsRepository.kt").readText()
        val reportFormatter = projectFile("app/src/main/java/com/soll/domain/deviceqa/DeviceQaReportFormatter.kt").readText()

        listOf(
            "NOTIFICATION_ANDROID13_FLOW",
            "NOTIFICATION_TAP_ROUTING",
            "THEME_VISUAL_PASS",
            "GADGET_PROTOCOL_SCHEMA",
            "GADGET_SERVER_LOCAL_BINDING",
            "GADGET_MESH_OUTBOX_WORKER",
            "GADGET_READ_ONLY_COMMAND_WORKER",
            "GADGET_MANUAL_WRITE_FLOW",
        ).forEach { id ->
            assertTrue("Missing Device QA id: $id", models.contains(id))
            assertTrue("Device QA repository does not expose: $id", repository.contains("DeviceQaCheckId.$id"))
            assertTrue("Settings action routing does not know: $id", settings.contains("DeviceQaCheckId.$id"))
        }
        assertTrue(models.contains("deviceSummary"))
        assertTrue(repository.contains("currentDeviceSummary"))
        assertTrue(repository.contains("currentAppSummary"))
        assertTrue(repository.contains("buildReport"))
        assertTrue(repository.contains("expectedResult ="))
        assertTrue(repository.contains("roadmapRef ="))
        assertTrue(settings.contains("Устройство:"))
        assertTrue(settings.contains("Ожидание:"))
        assertTrue(settings.contains("План:"))
        assertTrue(settings.contains("Отчет"))
        assertTrue(settings.contains("Поделиться"))
        assertTrue(settingsViewModel.contains("ACTION_SEND"))
        assertTrue(reportFormatter.contains("Отчет Device QA Soll App"))
        assertTrue(reportFormatter.contains("Ожидание:"))
        assertTrue(reportFormatter.contains("План:"))
        assertTrue(reportFormatter.contains("Версия приложения"))
        assertTrue(reportFormatter.contains("Статусы:"))
        assertTrue(settingsRepository.contains("KEY_DEVICE_QA_DEVICE_PREFIX"))
    }

    @Test
    fun `field map is offline first tool`() {
        val navigation = projectFile("app/src/main/java/com/soll/presentation/navigation/AppNavigation.kt").readText()
        val destinations = projectFile("app/src/main/java/com/soll/presentation/navigation/AppDestinations.kt").readText()
        val database = projectFile("app/src/main/java/com/soll/data/local/SollDatabase.kt").readText()
        val appModule = projectFile("app/src/main/java/com/soll/di/AppModule.kt").readText()
        val buildGradle = projectFile("app/build.gradle.kts").readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val notificationChannels = projectFile("app/src/main/java/com/soll/data/notification/SollNotificationChannels.kt").readText()
        val notificationModels = projectFile("app/src/main/java/com/soll/domain/notification/SollNotification.kt").readText()
        val bootReceiver = projectFile("app/src/main/java/com/soll/data/service/BootReceiver.kt").readText()
        val activityService = projectFile("app/src/main/java/com/soll/data/service/ActivityTrackingService.kt").readText()
        val fieldMapScreen = projectFile("app/src/main/java/com/soll/presentation/screens/tools/fieldmap/FieldMapScreen.kt").readText()

        assertTrue(projectFile("app/src/main/java/com/soll/presentation/screens/tools/fieldmap/FieldMapScreen.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/data/repository/FieldMapRepository.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/domain/field/FieldMapModels.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/data/service/ActivityTrackingService.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/data/repository/ActivityTrackingRepository.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/domain/activity/ActivityTrackingModels.kt").exists())
        assertFalse(destinations.contains("route = Routes.FIELD_MAP"))
        assertFalse(destinations.contains("title = \"Карта\""))
        assertTrue(destinations.contains("route = Routes.ACTIVITY_HISTORY"))
        assertTrue(destinations.contains("title = \"Активность\""))
        assertFalse(destinations.contains("Routes.LOCATION_PROCESSOR"))
        assertFalse(destinations.contains("Геопозиция для поиска"))
        assertFalse(navigation.contains("Routes.FIELD_MAP"))
        assertTrue(navigation.contains("Routes.ACTIVITY_HISTORY"))
        assertFalse(navigation.contains("Routes.LOCATION_PROCESSOR"))
        assertFalse(fieldMapScreen.contains("locationProcessorMode"))
        assertFalse(fieldMapScreen.contains("LocationProcessorHeader"))
        assertTrue(navigation.contains("initialActivityFocus = true"))
        assertTrue(database.contains("FieldPointEntity::class"))
        assertTrue(appModule.contains("migration17To18"))
        assertTrue(appModule.contains("field_points"))
        assertTrue(manifest.contains("FOREGROUND_SERVICE_LOCATION"))
        assertTrue(manifest.contains("ACTIVITY_RECOGNITION"))
        assertTrue(manifest.contains(".data.service.ActivityTrackingService"))
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
        assertFalse(buildGradle.contains("play-services-maps"))
        assertFalse(buildGradle.contains("mapbox"))
        assertFalse(buildGradle.contains("osmdroid"))
        assertTrue(notificationChannels.contains("ACTIVITY_TRACKING_NOTIFICATION_ID"))
        assertTrue(notificationModels.contains("ACTIVITY_TRACKING"))
        assertTrue(bootReceiver.contains("ActivityTrackingService.start"))
        assertTrue(activityService.contains("startForeground("))
        assertTrue(fieldMapScreen.contains("Запустить демон"))
    }

    @Test
    fun `portable SSD wiki stays read only SAF tool`() {
        val navigation = projectFile("app/src/main/java/com/soll/presentation/navigation/AppNavigation.kt").readText()
        val destinations = projectFile("app/src/main/java/com/soll/presentation/navigation/AppDestinations.kt").readText()
        val launchTargets = projectFile("app/src/main/java/com/soll/presentation/navigation/AppLaunchTargets.kt").readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val settingsRepository = projectFile("app/src/main/java/com/soll/data/repository/SettingsRepository.kt").readText()
        val repository = projectFile("app/src/main/java/com/soll/data/repository/PortableSsdRepository.kt").readText()
        val attachWorker = projectFile("app/src/main/java/com/soll/data/repository/PortableSsdAttachWorker.kt").readText()
        val attachReceiver = projectFile("app/src/main/java/com/soll/data/service/PortableSsdAttachReceiver.kt").readText()
        val screen = projectFile("app/src/main/java/com/soll/presentation/screens/tools/portablessd/PortableSsdScreen.kt").readText()
        val reader = projectFile("app/src/main/java/com/soll/domain/portablessd/PortableSsdModels.kt").readText()
        val notifications = projectFile("app/src/main/java/com/soll/domain/notification/SollNotification.kt").readText()

        assertTrue(destinations.contains("PORTABLE_SSD"))
        assertTrue(destinations.contains("SSD Wiki"))
        assertTrue(navigation.contains("PortableSsdScreen"))
        assertTrue(navigation.contains("SECTION_PORTABLE_SSD"))
        assertTrue(launchTargets.contains("SECTION_PORTABLE_SSD"))
        assertTrue(manifest.contains(".data.service.PortableSsdAttachReceiver"))
        assertTrue(manifest.contains("android.hardware.usb.action.USB_DEVICE_ATTACHED"))
        assertTrue(manifest.contains("android.intent.action.MEDIA_MOUNTED"))
        assertTrue(screen.contains("ActivityResultContracts.OpenDocumentTree"))
        assertTrue(repository.contains("takePersistableUriPermission"))
        assertTrue(repository.contains("cacheEntry(entry, sourceText, PortableSsdEntryContentSource.SSD)"))
        assertTrue(repository.contains("PortableSsdEntryContentSource.PHONE_CACHE"))
        assertTrue(repository.contains("portable-ssd-cache"))
        assertTrue(screen.contains("Скопировано на телефон"))
        assertTrue(screen.contains("Из памяти телефона"))
        assertTrue(settingsRepository.contains("KEY_PORTABLE_SSD_TREE_URI"))
        assertTrue(settingsRepository.contains("KEY_PORTABLE_SSD_LAST_ATTACH_NOTICE_AT"))
        assertTrue(attachWorker.contains("PortableSsdAttachNotificationPolicy.noticeFor"))
        assertTrue(attachWorker.contains("PORTABLE_SSD_NOTIFICATION_ID"))
        assertTrue(attachWorker.contains("SECTION_PORTABLE_SSD"))
        assertTrue(attachWorker.contains("PortableSsdAttachNoticeKind.VERIFIED"))
        assertTrue(attachWorker.contains("SollNotificationChannel.ALERTS"))
        assertTrue(attachReceiver.contains("PortableSsdAttachWorkScheduler.enqueue"))
        assertTrue(notifications.contains("launchSection"))
        assertTrue(reader.contains(".soll-portable"))
        assertTrue(reader.contains("server/.env").not())
        assertTrue(reader.contains("pairing.secret").not())
        assertFalse(repository.contains("FLAG_GRANT_WRITE_URI_PERMISSION"))
        assertFalse(repository.contains("createFile("))
        assertFalse(repository.contains("delete("))
    }

    @Test
    fun `server sync foreground service enters foreground before optional stop`() {
        val source = projectFile("app/src/main/java/com/soll/data/service/SollServerSyncForegroundService.kt").readText()
        val onStart = source.indexOf("override fun onStartCommand")
        val foregroundCall = source.indexOf("startSyncForeground()", startIndex = onStart)
        val actionStop = source.indexOf("if (intent?.action == ACTION_STOP)", startIndex = onStart)
        val blankSettingsStop = source.indexOf("if (settings.sollServerUrl.isBlank())", startIndex = onStart)

        assertTrue(foregroundCall > onStart)
        assertTrue(actionStop > foregroundCall)
        assertTrue(blankSettingsStop > foregroundCall)
        assertFalse(
            source.substring(onStart, foregroundCall).contains("stopSelf()"),
        )
        assertTrue(source.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
        assertTrue(source.contains(".setAction(ACTION_STOP)"))
    }

    @Test
    fun `core playback paths avoid blocking sleeps`() {
        val musicService = projectFile("app/src/main/java/com/soll/data/service/MusicPlaybackService.kt").readText()
        val ttsPlaybackFiles = listOf(
            "app/src/main/java/com/soll/domain/tts/NatashaTtsEngine.kt",
            "app/src/main/java/com/soll/domain/tts/UtrobinTtsEngine.kt",
            "app/src/main/java/com/soll/domain/tts/SileroJitEngine.kt",
            "app/src/main/java/com/soll/domain/tts/kokoro/KokoroOnnxTtsEngine.kt",
            "app/src/main/java/com/soll/domain/tts/chatterbox/ChatterboxOnnxTtsEngine.kt",
        )

        assertFalse(musicService.contains("runBlocking"))
        ttsPlaybackFiles.forEach { path ->
            val source = projectFile(path).readText()
            assertFalse("Blocking sleep remains in $path", source.contains("Thread.sleep"))
            assertTrue("TTS playback must stay coroutine-cancellable in $path", source.contains("delay("))
        }
    }

    @Test
    fun `tts pack io stays cancellation aware`() {
        val source = projectFile("app/src/main/java/com/soll/domain/tts/catalog/TtsPackLibrary.kt").readText()

        assertFalse(source.contains("while (true)"))
        assertTrue(source.contains("currentCoroutineContext().ensureActive()"))
        assertTrue(source.contains("catch (error: CancellationException)"))
        assertTrue(source.contains("call.cancel()"))
        assertTrue(source.contains("process.destroyForcibly()"))
    }

    @Test
    fun `tool job notifications do not swallow coroutine cancellation`() {
        val source = projectFile("app/src/main/java/com/soll/domain/tool/ToolJobRunner.kt").readText()

        assertFalse(source.contains("runCatching"))
        assertTrue(source.contains("catch (error: CancellationException)"))
        assertTrue(source.contains("throw error"))
    }

    @Test
    fun `network repositories preserve coroutine cancellation`() {
        val helper = projectFile("app/src/main/java/com/soll/data/repository/CoroutineResult.kt").readText()
        val soll = projectFile("app/src/main/java/com/soll/data/repository/SollRepository.kt").readText()
        val syncQueue = projectFile("app/src/main/java/com/soll/data/repository/SollSyncQueueRepository.kt").readText()

        assertTrue(helper.contains("catch (error: CancellationException)"))
        assertTrue(helper.contains("throw error"))
        assertFalse(soll.contains("runCatching"))
        assertTrue(soll.contains("runSuspendCatching"))
        assertTrue(syncQueue.contains("catch (error: CancellationException)"))
    }

    @Test
    fun `presentation long running loops declare cancellation`() {
        val presentationRoot = projectFile("app/src/main/java/com/soll/presentation")
        val offenders = presentationRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { source -> source.readText().contains("while (true)") }
            .map { it.relativeTo(presentationRoot).invariantSeparatorsPath }
            .toList()

        assertTrue("Presentation while(true) loops should use coroutine cancellation checks: $offenders", offenders.isEmpty())
    }

    @Test
    fun `main source avoids force unwrap crashes`() {
        val mainRoot = projectFile("app/src/main/java")
        val offenders = mainRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { source -> source.readText().contains("!!") }
            .map { it.relativeTo(mainRoot).invariantSeparatorsPath }
            .toList()

        assertTrue("Force unwrap remains in main source: $offenders", offenders.isEmpty())
    }

    @Test
    fun `presentation avoids inert empty click handlers`() {
        val presentationRoot = projectFile("app/src/main/java/com/soll/presentation")
        val offenders = presentationRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { source ->
                val text = source.readText()
                text.contains("onClick = { }") || text.contains("onClick = {}")
            }
            .map { it.relativeTo(presentationRoot).invariantSeparatorsPath }
            .toList()

        assertTrue("Empty click handlers remain in presentation: $offenders", offenders.isEmpty())
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n")

    private fun optionalProjectFile(path: String): File? {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        return null
    }
}
