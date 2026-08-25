package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePttSafetyGuardTest {
    @Test
    fun `ptt is foreground bounded and lifecycle cancelled`() {
        val screen = projectFile("app/src/main/java/com/soll/presentation/screens/voice/VoiceScreen.kt")
        val viewModel = projectFile("app/src/main/java/com/soll/presentation/screens/voice/VoiceViewModel.kt")
        val adapter = projectFile("app/src/main/java/com/soll/data/voice/AndroidSpeechRecognizerAdapter.kt")
        val contract = projectFile("app/src/main/java/com/soll/domain/voice/SttAdapter.kt")

        assertTrue(screen.contains("tryAwaitRelease()"))
        assertTrue(screen.contains("Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()"))
        assertTrue(screen.contains("Lifecycle.Event.ON_START -> viewModel.onScreenStarted()"))
        assertTrue(screen.contains("ActivityResultContracts.RequestMultiplePermissions()"))
        assertTrue(viewModel.contains("maxDurationMillis = MAX_PTT_DURATION_MS"))
        assertTrue(viewModel.contains("sttAdapter.cancelListening()"))
        assertTrue(adapter.contains("handler.postDelayed("))
        assertTrue(contract.contains("const val MAX_PTT_DURATION_MS = 30_000L"))
    }

    @Test
    fun `voice routes a connected Bluetooth headset and always releases it`() {
        val adapter = projectFile("app/src/main/java/com/soll/data/voice/AndroidSpeechRecognizerAdapter.kt")
        val router = projectFile("app/src/main/java/com/soll/data/voice/BluetoothSpeechAudioRouter.kt")
        val manifest = projectFile("app/src/main/AndroidManifest.xml")
        val chat = projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatScreen.kt")
        val voice = projectFile("app/src/main/java/com/soll/presentation/screens/voice/VoiceScreen.kt")

        assertTrue(adapter.contains("speechAudioRouter.prepareBluetoothInput()"))
        assertTrue(adapter.contains("speechAudioRouter.release()"))
        assertFalse(adapter.contains("SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30_000L"))
        assertTrue(router.contains("AudioDeviceInfo.TYPE_BLUETOOTH_SCO"))
        assertTrue(router.contains("AudioDeviceInfo.TYPE_BLE_HEADSET"))
        assertTrue(router.contains("setCommunicationDevice(headset)"))
        assertTrue(router.contains("clearCommunicationDevice()"))
        assertTrue(router.contains("AudioManager.MODE_IN_COMMUNICATION"))
        assertTrue(manifest.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
        assertTrue(chat.contains("Manifest.permission.BLUETOOTH_CONNECT"))
        assertTrue(voice.contains("Manifest.permission.BLUETOOTH_CONNECT"))
    }

    @Test
    fun `ptt screen is reachable from tools navigation`() {
        val destinations = projectFile("app/src/main/java/com/soll/presentation/navigation/AppDestinations.kt")
        val navigation = projectFile("app/src/main/java/com/soll/presentation/navigation/AppNavigation.kt")

        assertTrue(destinations.contains("const val VOICE = \"voice\""))
        assertTrue(destinations.contains("route = Routes.VOICE"))
        assertTrue(navigation.contains("composable(Routes.VOICE)"))
        assertTrue(navigation.contains("VoiceScreen("))
    }

    @Test
    fun `voice chat turn is forced read only and local tts is controllable`() {
        val turn = projectFile("app/src/main/java/com/soll/domain/voice/VoiceAssistantTurn.kt")
        val viewModel = projectFile("app/src/main/java/com/soll/presentation/screens/voice/VoiceViewModel.kt")
        val tts = projectFile("app/src/main/java/com/soll/domain/tts/TextToSpeechManager.kt")

        assertTrue(turn.contains("val taskIntake: Boolean = false"))
        assertTrue(turn.contains("val allowActions: Boolean = false"))
        assertTrue(viewModel.contains("taskIntake = turn.taskIntake"))
        assertTrue(viewModel.contains("allowActions = turn.allowActions"))
        assertTrue(viewModel.contains("ttsManager.speakAssistantResponse(clean)"))
        assertTrue(viewModel.contains("ttsManager.stop()"))
        assertTrue(viewModel.contains("isScreenForeground && !_uiState.value.isMuted"))
        assertTrue(tts.contains("_engineType.value = TtsEngineType.SYSTEM"))
    }

    @Test
    fun `regular Android chat is also fail closed`() {
        val chatViewModel = projectFile(
            "app/src/main/java/com/soll/presentation/screens/chat/ChatViewModel.kt"
        )
        val gateway = projectFile("app/src/main/java/com/soll/domain/soll/SollGateway.kt")
        val api = projectFile("app/src/main/java/com/soll/data/api/SollApiService.kt")

        assertTrue(chatViewModel.contains("taskIntake = false"))
        assertTrue(chatViewModel.contains("allowActions = false"))
        assertTrue(gateway.contains("taskIntake: Boolean = false"))
        assertTrue(gateway.contains("allowActions: Boolean = false"))
        assertTrue(api.contains("val taskIntake: Boolean = false"))
        assertTrue(api.contains("val allowActions: Boolean = false"))
    }

    @Test
    fun `voice does not introduce background microphone infrastructure`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml")
        val voiceTree = File(projectRoot(), "app/src/main/java/com/soll")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.path.contains("voice", ignoreCase = true) }
            .joinToString("\n") { it.readText() }

        assertFalse(manifest.contains("FOREGROUND_SERVICE_MICROPHONE"))
        assertFalse(voiceTree.contains("startForegroundService"))
        assertFalse(voiceTree.contains("VoiceInteractionService"))
        assertFalse(voiceTree.contains("AlwaysOnHotwordDetector"))
    }

    @Test
    fun `retired action capable voice router stays removed`() {
        val root = projectRoot()

        assertFalse(File(root, "app/src/main/java/com/soll/domain/voice/VoiceCommandRouter.kt").exists())
        assertFalse(File(root, "app/src/main/java/com/soll/domain/voice/VoiceCommandParser.kt").exists())
        assertFalse(File(root, "app/src/main/java/com/soll/data/music/MusicVoiceController.kt").exists())
    }

    private fun projectFile(relativePath: String): String =
        File(projectRoot(), relativePath).readText()

    private fun projectRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(6) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: error("Project root not found")
        }
        error("Project root not found")
    }
}
