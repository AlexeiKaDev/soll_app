package com.soll.domain.reader

enum class ReaderTheme(val storageKey: String) {
    LIGHT("light"),
    SEPIA("sepia"),
    DARK("dark"),
    EYE_COMFORT("eye_comfort");

    companion object {
        fun fromStorage(value: String?): ReaderTheme = entries.firstOrNull { it.storageKey == value } ?: DARK
    }
}

enum class ReaderFontFamily(val storageKey: String) {
    CARLSBERG("carlsberg"),
    SERIF("serif"),
    SANS_SERIF("sans_serif"),
    MONOSPACE("monospace");

    companion object {
        fun fromStorage(value: String?): ReaderFontFamily = entries.firstOrNull { it.storageKey == value } ?: CARLSBERG
    }
}

enum class ReaderTextAlignment(val storageKey: String) {
    START("start"),
    JUSTIFY("justify"),
    CENTER("center");

    companion object {
        fun fromStorage(value: String?): ReaderTextAlignment = entries.firstOrNull { it.storageKey == value } ?: JUSTIFY
    }
}

data class ReaderAppearance(
    val theme: ReaderTheme = ReaderTheme.DARK,
    val fontSizeSp: Float = 19f,
    val lineSpacingMultiplier: Float = 1.5f,
    val horizontalMarginDp: Float = 18f,
    val screenBrightness: Float? = null,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.CARLSBERG,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.JUSTIFY,
) {
    fun normalized(): ReaderAppearance = copy(
        fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP),
        lineSpacingMultiplier = lineSpacingMultiplier.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING),
        horizontalMarginDp = horizontalMarginDp.coerceIn(MIN_MARGIN_DP, MAX_MARGIN_DP),
        screenBrightness = screenBrightness?.coerceIn(MIN_SCREEN_BRIGHTNESS, MAX_SCREEN_BRIGHTNESS),
    )

    companion object {
        const val MIN_FONT_SIZE_SP = 14f
        const val MAX_FONT_SIZE_SP = 34f
        const val MIN_LINE_SPACING = 1.1f
        const val MAX_LINE_SPACING = 2.0f
        const val MIN_MARGIN_DP = 8f
        const val MAX_MARGIN_DP = 40f
        const val MIN_SCREEN_BRIGHTNESS = 0.05f
        const val MAX_SCREEN_BRIGHTNESS = 1f
    }
}
