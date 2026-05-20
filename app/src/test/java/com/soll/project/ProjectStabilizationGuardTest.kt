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
            "app/src/main/java/com/soll/data/repository/TelegramRepository.kt",
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

        assertFalse(appModule.contains("fallbackToDestructiveMigration()"))
        assertTrue(appModule.contains("migration1To2"))
        assertTrue(appModule.contains("migration3To4"))
    }

    @Test
    fun `manifest keeps release safer defaults`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"\${usesCleartextTraffic}\""))
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
    }

    @Test
    fun `requested tool widgets stay registered`() {
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

        assertTrue(manifest.contains(".presentation.widgets.MusicWidgetProvider"))
        assertTrue(manifest.contains(".presentation.widgets.ReaderWidgetProvider"))
        assertTrue(manifest.contains(".presentation.widgets.NotesWidgetProvider"))
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

        assertTrue(themeVariant.contains("CLASSIC"))
        assertTrue(themeVariant.contains("AURORA"))
        assertTrue(themeVariant.contains("AQUIK"))
        assertTrue(theme.contains("ClassicDarkColorScheme"))
        assertTrue(theme.contains("AuroraDarkColorScheme"))
        assertTrue(theme.contains("AquikDarkColorScheme"))
        assertTrue(repository.contains("\"aquik\""))
        assertTrue(settings.contains("Тема"))
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
            "NOTIFICATION_MEDIA_SESSION",
            "MUSIC_SCREEN_OFF",
            "MUSIC_LOCKSCREEN_CONTROLS",
            "MUSIC_AUDIO_FOCUS",
            "WIDGET_LAUNCHER_COLD",
            "WIDGET_MEDIA_CONTROLS",
            "THEME_VISUAL_PASS",
            "GADGET_PROTOCOL_SCHEMA",
            "GADGET_SERVER_LOCAL_BINDING",
            "NFC_OWNED_TAGS",
            "NFC_ACCESS_FOB_DIAGNOSTIC",
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

        assertTrue(projectFile("app/src/main/java/com/soll/presentation/screens/tools/fieldmap/FieldMapScreen.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/data/repository/FieldMapRepository.kt").exists())
        assertTrue(projectFile("app/src/main/java/com/soll/domain/field/FieldMapModels.kt").exists())
        assertTrue(destinations.contains("route = Routes.FIELD_MAP"))
        assertTrue(destinations.contains("title = \"Карта\""))
        assertTrue(navigation.contains("Routes.FIELD_MAP"))
        assertTrue(database.contains("FieldPointEntity::class"))
        assertTrue(appModule.contains("migration17To18"))
        assertTrue(appModule.contains("field_points"))
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
        assertFalse(buildGradle.contains("play-services-maps"))
        assertFalse(buildGradle.contains("mapbox"))
        assertFalse(buildGradle.contains("osmdroid"))
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
        val telegram = projectFile("app/src/main/java/com/soll/data/repository/TelegramRepository.kt").readText()
        val soll = projectFile("app/src/main/java/com/soll/data/repository/SollRepository.kt").readText()
        val syncQueue = projectFile("app/src/main/java/com/soll/data/repository/SollSyncQueueRepository.kt").readText()

        assertTrue(helper.contains("catch (error: CancellationException)"))
        assertTrue(helper.contains("throw error"))
        assertFalse(telegram.contains("runCatching"))
        assertFalse(soll.contains("runCatching"))
        assertTrue(telegram.contains("runSuspendCatching"))
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
}
