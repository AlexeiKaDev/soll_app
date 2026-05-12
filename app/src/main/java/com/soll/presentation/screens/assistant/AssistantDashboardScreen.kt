package com.soll.presentation.screens.assistant

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.presentation.screens.home.HomeScreen
import com.soll.presentation.screens.home.HomeViewModel

@Composable
fun AssistantDashboardScreen(
    viewModel: AssistantStatusViewModel = hiltViewModel(),
) {
    HomeScreen(viewModel = viewModel)
}

typealias AssistantStatusViewModel = HomeViewModel
