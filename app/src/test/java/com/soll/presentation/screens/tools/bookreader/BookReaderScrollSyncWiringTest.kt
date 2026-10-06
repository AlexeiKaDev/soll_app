package com.soll.presentation.screens.tools.bookreader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the fix for silent (non-TTS) reading never reaching the server: before this, only
 * ttsManager.currentWordRange and explicit jumps ever updated currentChapterPosition, so a
 * user who just scrolls through a book had their progress silently stuck wherever a jump or
 * TTS session last left it -- persistCurrentProgress() on exit re-saved that stale position
 * instead of where the user actually stopped reading.
 */
class BookReaderScrollSyncWiringTest {
    @Test
    fun `manual scrolling reports a position back to the view model`() {
        val screen = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tools/bookreader/BookReaderScreen.kt"
        ).readText()

        assertTrue(
            "BookReadingScreen must accept a callback for manual scroll position changes",
            screen.contains("onReadingPositionChanged: (Int) -> Unit"),
        )
        assertTrue(
            "The call site must wire it to the view model, not leave it unused",
            screen.contains("onReadingPositionChanged = viewModel::updateReadingPosition"),
        )
        assertTrue(
            "Scroll tracking must skip while TTS drives the scroll itself",
            Regex(
                "LaunchedEffect\\(currentChapter, isTtsPlaying\\) \\{\\s*if \\(isTtsPlaying\\) return@LaunchedEffect",
            ).containsMatchIn(screen),
        )
    }

    @Test
    fun `updateReadingPosition reuses the existing throttled save path instead of a new one`() {
        val viewModel = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tools/bookreader/BookReaderViewModel.kt"
        ).readText()

        val body = Regex(
            "fun updateReadingPosition\\(position: Int\\) \\{.*?\\n    \\}",
            RegexOption.DOT_MATCHES_ALL,
        ).find(viewModel)?.value
        requireNotNull(body) { "updateReadingPosition() not found in BookReaderViewModel.kt" }
        assertTrue(
            "Must update currentChapterPosition so persistCurrentProgress() on exit has a real position",
            body.contains("currentChapterPosition = position"),
        )
        assertTrue(
            "Must reuse saveProgressIfNeeded (the same 480-char/15s throttle TTS already uses), " +
                "not a second bespoke save path",
            body.contains("saveProgressIfNeeded(position)"),
        )
    }

    @Test
    fun `scroll-to-offset mapping reverses the existing offset-to-scroll jump mapping`() {
        val screen = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tools/bookreader/BookReaderScreen.kt"
        ).readText()

        // The jump effect converts offset -> line -> lineTop -> scroll. The new scroll listener
        // must convert the other way (scroll -> approx line top -> line -> offset) using the
        // same Layout, or the two will disagree about where the reader actually is after a jump
        // followed by a small manual scroll.
        assertTrue(screen.contains("layout.getLineForVertical(approxTop)"))
        assertTrue(screen.contains("layout.getLineStart(line)"))
        assertTrue(
            "The debounced collector must guard a null layout instead of crashing on NPE",
            screen.contains("val layout = readerTextView?.layout ?: return@collect"),
        )
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path")
    }
}
