package com.soll.presentation.screens.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.soll.data.repository.SettingsRepository
import com.soll.ui.theme.SollTheme
import com.soll.ui.theme.SollThemeVariant
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DailyTodoActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeVariantKey by settingsRepository.appThemeVariantFlow.collectAsState(
                initial = settingsRepository.appThemeVariant,
            )
            SollTheme(variant = SollThemeVariant.fromStorage(themeVariantKey)) {
                DailyTodoScreen(onBack = ::finish)
            }
        }
    }
}
