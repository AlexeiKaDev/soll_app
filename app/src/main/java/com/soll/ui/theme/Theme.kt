package com.soll.ui.theme

import android.app.Activity
import android.view.View
import android.view.Window
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SollLightColorScheme = lightColorScheme(
    primary = SollLightPrimary,
    onPrimary = SollLightOnPrimary,
    primaryContainer = SollLightPrimaryContainer,
    onPrimaryContainer = SollLightOnPrimaryContainer,
    secondary = SollLightSecondary,
    onSecondary = SollLightOnSecondary,
    secondaryContainer = SollLightSecondaryContainer,
    onSecondaryContainer = SollLightOnSecondaryContainer,
    tertiary = SollLightTertiary,
    onTertiary = SollLightOnTertiary,
    tertiaryContainer = SollLightTertiaryContainer,
    onTertiaryContainer = SollLightOnTertiaryContainer,
    error = SollLightError,
    errorContainer = SollLightErrorContainer,
    onError = SollLightOnError,
    onErrorContainer = SollLightOnErrorContainer,
    background = SollLightBackground,
    onBackground = SollLightOnBackground,
    surface = SollLightSurface,
    onSurface = SollLightOnSurface,
    surfaceVariant = SollLightSurfaceVariant,
    onSurfaceVariant = SollLightOnSurfaceVariant,
    outline = SollLightOutline,
    inverseOnSurface = SollLightInverseOnSurface,
    inverseSurface = SollLightInverseSurface,
    inversePrimary = SollLightInversePrimary
)

private val ClassicDarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary
)

private val AuroraDarkColorScheme = darkColorScheme(
    primary = AuroraDarkPrimary,
    onPrimary = AuroraDarkOnPrimary,
    primaryContainer = AuroraDarkPrimaryContainer,
    onPrimaryContainer = AuroraDarkOnPrimaryContainer,
    secondary = AuroraDarkSecondary,
    onSecondary = AuroraDarkOnSecondary,
    secondaryContainer = AuroraDarkSecondaryContainer,
    onSecondaryContainer = AuroraDarkOnSecondaryContainer,
    tertiary = AuroraDarkTertiary,
    onTertiary = AuroraDarkOnTertiary,
    tertiaryContainer = AuroraDarkTertiaryContainer,
    onTertiaryContainer = AuroraDarkOnTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = AuroraDarkBackground,
    onBackground = AuroraDarkOnBackground,
    surface = AuroraDarkSurface,
    onSurface = AuroraDarkOnSurface,
    surfaceVariant = AuroraDarkSurfaceVariant,
    onSurfaceVariant = AuroraDarkOnSurfaceVariant,
    outline = AuroraDarkOutline,
    inverseOnSurface = AuroraDarkInverseOnSurface,
    inverseSurface = AuroraDarkInverseSurface,
    inversePrimary = AuroraDarkInversePrimary
)

private val AquikDarkColorScheme = darkColorScheme(
    primary = AquikDarkPrimary,
    onPrimary = AquikDarkOnPrimary,
    primaryContainer = AquikDarkPrimaryContainer,
    onPrimaryContainer = AquikDarkOnPrimaryContainer,
    secondary = AquikDarkSecondary,
    onSecondary = AquikDarkOnSecondary,
    secondaryContainer = AquikDarkSecondaryContainer,
    onSecondaryContainer = AquikDarkOnSecondaryContainer,
    tertiary = AquikDarkTertiary,
    onTertiary = AquikDarkOnTertiary,
    tertiaryContainer = AquikDarkTertiaryContainer,
    onTertiaryContainer = AquikDarkOnTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = AquikDarkBackground,
    onBackground = AquikDarkOnBackground,
    surface = AquikDarkSurface,
    onSurface = AquikDarkOnSurface,
    surfaceVariant = AquikDarkSurfaceVariant,
    onSurfaceVariant = AquikDarkOnSurfaceVariant,
    outline = AquikDarkOutline,
    inverseOnSurface = AquikDarkInverseOnSurface,
    inverseSurface = AquikDarkInverseSurface,
    inversePrimary = AquikDarkInversePrimary
)

@Composable
fun SollTheme(
    variant: SollThemeVariant = SollThemeVariant.default,
    content: @Composable () -> Unit
) {
    val colorScheme = when (variant) {
        SollThemeVariant.SOLL -> SollLightColorScheme
        SollThemeVariant.CLASSIC -> ClassicDarkColorScheme
        SollThemeVariant.AURORA -> AuroraDarkColorScheme
        SollThemeVariant.AQUIK -> AquikDarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            applySystemBarColors(window, view, variant, colorScheme)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Suppress("DEPRECATION")
private fun applySystemBarColors(
    window: Window,
    view: View,
    variant: SollThemeVariant,
    colorScheme: ColorScheme
) {
    window.statusBarColor = when (variant) {
        SollThemeVariant.SOLL -> colorScheme.background.toArgb()
        SollThemeVariant.CLASSIC -> colorScheme.primary.toArgb()
        SollThemeVariant.AURORA -> colorScheme.background.toArgb()
        SollThemeVariant.AQUIK -> colorScheme.primary.toArgb()
    }
    val insets = WindowCompat.getInsetsController(window, view)
    val useLightSystemBars = variant == SollThemeVariant.SOLL
    insets.isAppearanceLightStatusBars = useLightSystemBars
    // Системная навигация (жесты): тот же фон, что и контент / нижняя панель - без серой полосы.
    window.navigationBarColor = colorScheme.background.toArgb()
    insets.isAppearanceLightNavigationBars = useLightSystemBars
}
