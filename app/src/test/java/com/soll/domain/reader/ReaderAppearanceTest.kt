package com.soll.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAppearanceTest {
    @Test
    fun `normalization keeps persisted numeric values inside supported ranges`() {
        val normalized = ReaderAppearance(
            fontSizeSp = 200f,
            lineSpacingMultiplier = -4f,
            horizontalMarginDp = 100f,
            screenBrightness = 4f,
        ).normalized()

        assertEquals(ReaderAppearance.MAX_FONT_SIZE_SP, normalized.fontSizeSp)
        assertEquals(ReaderAppearance.MIN_LINE_SPACING, normalized.lineSpacingMultiplier)
        assertEquals(ReaderAppearance.MAX_MARGIN_DP, normalized.horizontalMarginDp)
        assertEquals(ReaderAppearance.MAX_SCREEN_BRIGHTNESS, normalized.screenBrightness)
    }

    @Test
    fun `storage keys restore every supported appearance option`() {
        ReaderTheme.entries.forEach { assertEquals(it, ReaderTheme.fromStorage(it.storageKey)) }
        ReaderFontFamily.entries.forEach { assertEquals(it, ReaderFontFamily.fromStorage(it.storageKey)) }
        ReaderTextAlignment.entries.forEach { assertEquals(it, ReaderTextAlignment.fromStorage(it.storageKey)) }
    }

    @Test
    fun `unknown persisted keys use readable defaults`() {
        assertEquals(ReaderTheme.DARK, ReaderTheme.fromStorage("removed-theme"))
        assertEquals(ReaderFontFamily.CARLSBERG, ReaderFontFamily.fromStorage(null))
        assertEquals(ReaderTextAlignment.JUSTIFY, ReaderTextAlignment.fromStorage(""))
    }
}
