package com.soll.presentation.screens.today

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.soll.SollFeedItem
import com.soll.domain.soll.SollTodayCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::onCalendarPermissionResult,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Сегодня", fontWeight = FontWeight.Bold)
                        if (state.offline) Text("сохранённый снимок", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.selectedTab == TodayTab.TODAY,
                    onClick = { viewModel.selectTab(TodayTab.TODAY) },
                    label = { Text("Брифинг") },
                )
                FilterChip(
                    selected = state.selectedTab == TodayTab.FEED,
                    onClick = { viewModel.selectTab(TodayTab.FEED) },
                    label = { Text("Лента ${state.feed.size}") },
                )
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            if (state.loading && state.snapshot == null) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (state.selectedTab == TodayTab.TODAY) {
                val snapshot = state.snapshot
                val briefingCards = snapshot?.briefingCards.orEmpty()
                val briefingKeys = stableTodayCardKeys(briefingCards)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(
                            snapshot?.summary ?: "Снимок пока не готов",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    snapshot?.nextAction?.takeIf { it.title.isNotBlank() }?.let { action ->
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Следующий шаг", style = MaterialTheme.typography.labelLarge)
                                    Text(action.title, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                if (state.calendarPermissionGranted) viewModel.syncCalendar()
                                else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                            },
                            enabled = !state.calendarSyncing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Text(
                                if (state.calendarPermissionGranted) "  Обновить календарь"
                                else "  Подключить календарь (только чтение)"
                            )
                        }
                    }
                    itemsIndexed(
                        briefingCards,
                        key = { index, _ -> briefingKeys[index] },
                    ) { _, card ->
                        TodayCard(card = card, onOpenUrl = { url -> openUrl(context, url) })
                    }
                    snapshot?.warnings.orEmpty().takeIf { it.isNotEmpty() }?.let { warnings ->
                        item {
                            Text("Ограничения: ${warnings.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            } else {
                val feedKeys = stableFeedItemKeys(state.feed)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(
                        state.feed,
                        key = { index, _ -> feedKeys[index] },
                    ) { _, item ->
                        FeedCard(
                            item = item,
                            feedbackBusy = state.feedbackItemId == item.id,
                            onUseful = { viewModel.sendFeedback(item, "useful") },
                            onNotUseful = { viewModel.sendFeedback(item, "not_useful") },
                            onOpenUrl = { openUrl(context, item.url) },
                        )
                    }
                    if (state.feedHasMore) {
                        item {
                            Button(
                                onClick = viewModel::loadMore,
                                enabled = !state.loadingMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (state.loadingMore) "Загрузка…" else "Показать ещё") }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

internal fun stableTodayCardKeys(cards: List<SollTodayCard>): List<String> =
    stableUiKeys(
        items = cards,
        prefix = "today-card",
    ) { card ->
        listOf(card.id, card.kind, card.title, card.url, card.summary).joinToString("\u001f")
    }

internal fun stableFeedItemKeys(items: List<SollFeedItem>): List<String> =
    stableUiKeys(
        items = items,
        prefix = "feed-item",
    ) { item ->
        listOf(item.id, item.sourceId, item.title, item.url, item.publishedAt).joinToString("\u001f")
    }

private fun <T> stableUiKeys(
    items: List<T>,
    prefix: String,
    identity: (T) -> String,
): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return items.map { item ->
        val identityValue = identity(item)
        val occurrence = occurrences.getOrDefault(identityValue, 0)
        occurrences[identityValue] = occurrence + 1
        "$prefix:$identityValue#$occurrence"
    }
}

@Composable
private fun TodayCard(card: SollTodayCard, onOpenUrl: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(card.summary, style = MaterialTheme.typography.bodyMedium)
            if (card.whyForYou.isNotBlank()) {
                Text("Почему для вас: ${card.whyForYou}", style = MaterialTheme.typography.bodySmall)
            }
            if (card.url.isNotBlank()) {
                IconButton(onClick = { onOpenUrl(card.url) }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Открыть источник")
                }
            }
        }
    }
}

@Composable
private fun FeedCard(
    item: SollFeedItem,
    feedbackBusy: Boolean,
    onUseful: () -> Unit,
    onNotUseful: () -> Unit,
    onOpenUrl: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.sourceName, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("${item.rankScore}/100", style = MaterialTheme.typography.labelMedium)
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(item.summary, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Text("Почему для вас: ${item.whyForYou}", style = MaterialTheme.typography.bodySmall)
            item.assessment?.let { assessment ->
                Text(
                    "Применимость: ${assessment.readiness}; цели: ${assessment.targets.joinToString()}; риск: ${assessment.risk}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (assessment.experiment.isNotBlank()) {
                    Text("Следующий тест: ${assessment.experiment}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUseful, enabled = !feedbackBusy) {
                    Icon(Icons.Default.ThumbUp, contentDescription = "Полезно")
                }
                IconButton(onClick = onNotUseful, enabled = !feedbackBusy) {
                    Icon(Icons.Default.ThumbDown, contentDescription = "Не полезно")
                }
                IconButton(onClick = onOpenUrl, enabled = item.url.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Открыть источник")
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
