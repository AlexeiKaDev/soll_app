package com.soll.presentation.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.soll.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.soll.SollChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput()
        } else {
            viewModel.onVoicePermissionDenied()
        }
    }
    val visibleMessages = remember(uiState.messages, uiState.searchQuery) {
        visibleChatMessages(uiState.messages, uiState.searchQuery)
    }
    val hasHistoryLoader = uiState.hasMoreHistory || uiState.isLoadingOlder
    val showJumpToBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible < total - 1
        }
    }

    LaunchedEffect(uiState.scrollToBottomToken) {
        val token = uiState.scrollToBottomToken
        if (token > 0) {
            if (visibleMessages.isNotEmpty() && uiState.searchQuery.isBlank()) {
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                if (
                    shouldAutoScrollChatList(
                        reason = uiState.scrollToBottomReason,
                        totalItemsCount = layoutInfo.totalItemsCount,
                        lastVisibleIndex = lastVisibleIndex,
                    )
                ) {
                    listState.animateScrollToItem(chatLastMessageListIndex(visibleMessages.size, hasHistoryLoader))
                }
            }
            viewModel.onScrollRequestHandled(token)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ai_robot_notification),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Чат Soll",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = chatSubtitle(uiState),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск по чату")
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить чат")
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                value = uiState.input,
                isSending = uiState.isSending,
                isVoiceAvailable = uiState.isVoiceAvailable,
                isVoiceListening = uiState.isVoiceListening,
                voicePartialText = uiState.voicePartialText,
                voiceError = uiState.voiceError,
                onValueChange = viewModel::onInputChanged,
                onSend = viewModel::send,
                onVoiceClick = {
                    if (uiState.isVoiceListening) {
                        viewModel.stopVoiceInput()
                    } else if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.startVoiceInput()
                    } else {
                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onDismissVoiceError = viewModel::dismissVoiceError,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.pendingActionsCount > 0 || uiState.encrypted) {
                ChatStatusRow(uiState = uiState, onRefresh = viewModel::refresh)
            }

            if (uiState.isSearchOpen) {
                ChatSearchBar(
                    query = uiState.searchQuery,
                    resultCount = visibleMessages.size,
                    totalCount = uiState.messages.size,
                    onQueryChange = viewModel::onSearchChanged,
                    onClose = viewModel::closeSearch,
                )
            }

            uiState.error?.let { error ->
                ChatErrorBanner(error = error, onOpenSettings = onOpenSettings, onRetry = viewModel::refresh)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
            ) {
                if (uiState.isLoading && uiState.messages.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.messages.isEmpty()) {
                    EmptyChatState(
                        hasError = uiState.error != null,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (visibleMessages.isEmpty()) {
                    EmptySearchState(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (hasHistoryLoader) {
                            item(key = "history-loader", contentType = "loader") {
                                HistoryLoader(
                                    isLoading = uiState.isLoadingOlder,
                                    hasMore = uiState.hasMoreHistory,
                                    onLoad = viewModel::loadOlderMessages,
                                )
                            }
                        }
                        item(key = "date-today") {
                            ChatDatePill(text = "Сегодня")
                        }
                        items(
                            visibleMessages,
                            key = { it.id },
                            contentType = { if (it.isFromUser) "user" else "assistant" },
                        ) { message ->
                            ChatMessageBubble(
                                message = message,
                                isBusy = uiState.isSending,
                                onAction = viewModel::executeAction,
                            )
                        }
                    }
                    if (showJumpToBottom) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        visibleMessages.lastIndex + 1 + if (hasHistoryLoader) 1 else 0,
                                    )
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(18.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Вниз")
                        }
                    }
                }
            }
        }
    }
}

internal fun visibleChatMessages(
    messages: List<SollChatMessage>,
    searchQuery: String,
): List<SollChatMessage> {
    if (searchQuery.isBlank()) return messages
    return messages.filter { message -> message.matchesChatQuery(searchQuery) }
}

internal fun chatLastMessageListIndex(messageCount: Int, hasHistoryLoader: Boolean): Int =
    (messageCount - 1).coerceAtLeast(0) + 1 + if (hasHistoryLoader) 1 else 0

internal fun shouldAutoScrollChatList(
    reason: ChatScrollReason,
    totalItemsCount: Int,
    lastVisibleIndex: Int?,
): Boolean =
    when (reason) {
        ChatScrollReason.NONE -> false
        ChatScrollReason.INITIAL_LOAD,
        ChatScrollReason.SESSION_CHANGED,
        ChatScrollReason.USER_SEND -> true
        ChatScrollReason.REMOTE_APPEND -> {
            lastVisibleIndex != null &&
                (totalItemsCount <= 0 || lastVisibleIndex >= totalItemsCount - CHAT_AUTO_SCROLL_BOTTOM_THRESHOLD)
        }
    }

private const val CHAT_AUTO_SCROLL_BOTTOM_THRESHOLD = 3

@Composable
private fun HistoryLoader(
    isLoading: Boolean,
    hasMore: Boolean,
    onLoad: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else if (hasMore) {
            TextButton(onClick = onLoad) {
                Text("Загрузить историю")
            }
        }
    }
}

@Composable
private fun ChatSearchBar(
    query: String,
    resultCount: Int,
    totalCount: Int,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                    }
                }
            },
            placeholder = { Text("Поиск") },
            supportingText = {
                Text(
                    text = if (query.isBlank()) "$totalCount сообщений" else "$resultCount из $totalCount",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            shape = RoundedCornerShape(18.dp),
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Закрыть поиск")
        }
    }
}

@Composable
private fun ChatStatusRow(
    uiState: ChatUiState,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uiState.encrypted) {
            AssistChip(
                onClick = onRefresh,
                enabled = false,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = { Text("AES-GCM") },
            )
        }
        if (uiState.pendingActionsCount > 0) {
            AssistChip(
                onClick = onRefresh,
                label = { Text("Действий: ${uiState.pendingActionsCount}") },
            )
        }
    }
}

@Composable
private fun ChatErrorBanner(
    error: String,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val friendly = friendlyChatError(error)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.74f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = friendly.first,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = friendly.second,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Проверить")
                }
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Настройки")
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(
    hasError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = if (hasError) "Жду сервер" else "Чат пуст",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (hasError) {
                "После настройки API здесь появятся сообщения Soll и события сервера."
            } else {
                "Напишите сообщение Soll или дождитесь события сервера."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySearchState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = "Ничего не найдено",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Измените запрос или очистите поиск.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatDatePill(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: SollChatMessage,
    isBusy: Boolean,
    onAction: (ChatActionUi) -> Unit,
) {
    val background = if (message.isFromUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val foreground = if (message.isFromUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bubbleShape = if (message.isFromUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!message.isFromUser) {
            SollAvatar()
            Spacer(modifier = Modifier.size(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (message.isFromUser) 0.80f else 0.78f)
                .background(background, bubbleShape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (message.isFromUser) {
                LinkifiedChatText(
                    text = message.content,
                    color = foreground,
                    linkColor = foreground.copy(alpha = 0.96f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ChatBubbleMeta(
                    text = formatChatTimeLabel(message.createdAt),
                    isUser = true,
                    color = foreground,
                )
            } else {
                AssistantMessageContent(
                    message = message,
                    isBusy = isBusy,
                    foreground = foreground,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageContent(
    message: SollChatMessage,
    isBusy: Boolean,
    foreground: androidx.compose.ui.graphics.Color,
    onAction: (ChatActionUi) -> Unit,
) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val isLong = message.content.length > 700 || message.content.count { it == '\n' } > 10
    messageTitle(message)?.let { title ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = foreground.copy(alpha = 0.86f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatChatTimeLabel(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.56f),
            )
        }
    }
    ChatBadgeRow(message)
    LinkifiedChatText(
        text = message.content,
        color = foreground,
        linkColor = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (isLong && !expanded) 8 else Int.MAX_VALUE,
        overflow = if (isLong && !expanded) TextOverflow.Ellipsis else TextOverflow.Clip,
    )
    message.linkPreviewOrNull()?.let { preview ->
        LinkPreviewCard(preview = preview)
    }
    if (isLong) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Свернуть" else "Развернуть")
        }
    }
    val actions = message.actionUis()
    if (actions.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                Button(
                    onClick = { onAction(action) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(action.label)
                }
            }
        }
    }
}

@Composable
private fun ChatBadgeRow(message: SollChatMessage) {
    val badges = message.badgeUis()
    if (badges.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        badges.forEach { badge ->
            ChatBadge(badge = badge)
        }
    }
}

@Composable
private fun ChatBadge(badge: ChatBadgeUi) {
    val style = badge.style()
    Surface(
        color = style.containerColor,
        contentColor = style.contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = badge.text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatBadgeUi.style(): ChatBadgeStyle =
    when (kind) {
        ChatBadgeKind.STATUS -> {
            val normalized = text.lowercase()
            when {
                normalized in setOf("done", "ok", "success", "completed", "готово") -> {
                    ChatBadgeStyle(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                normalized in setOf("failed", "error", "ошибка") -> {
                    ChatBadgeStyle(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                else -> {
                    ChatBadgeStyle(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        ChatBadgeKind.SECURITY -> ChatBadgeStyle(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ChatBadgeKind.TASK -> ChatBadgeStyle(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ChatBadgeKind.SOURCE -> ChatBadgeStyle(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChatBadgeKind.INFO -> ChatBadgeStyle(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ChatBadgeKind.SUCCESS -> ChatBadgeStyle(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ChatBadgeKind.WARNING -> ChatBadgeStyle(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ChatBadgeKind.DANGER -> ChatBadgeStyle(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }

@Composable
private fun LinkifiedChatText(
    text: String,
    color: Color,
    linkColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val links = remember(text) { chatLinkMatches(text) }
    if (links.isEmpty()) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, links, linkColor) {
        buildAnnotatedString {
            append(text)
            links.forEach { link ->
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                    start = link.start,
                    end = link.end,
                )
                addStringAnnotation(
                    tag = CHAT_LINK_ANNOTATION_TAG,
                    annotation = link.url,
                    start = link.start,
                    end = link.end,
                )
            }
        }
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
        onClick = { offset ->
            annotated
                .getStringAnnotations(CHAT_LINK_ANNOTATION_TAG, offset, offset)
                .firstOrNull()
                ?.let { annotation -> uriHandler.openUri(annotation.item) }
        },
    )
}

@Composable
private fun LinkPreviewCard(preview: Map<*, *>) {
    val title = preview["title"]?.toString().orEmpty()
    val site = preview["site_name"]?.toString().orEmpty()
    val url = preview["url"]?.toString().orEmpty()
    val description = preview["description"]?.toString().orEmpty()
    val imageUrl = preview["image_url"]?.toString().orEmpty()
    if (title.isBlank() && url.isBlank()) return
    val uriHandler = LocalUriHandler.current
    val openableUrl = url.takeIf { isOpenableChatUrl(it) }
    val cardModifier = if (openableUrl != null) {
        Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(openableUrl) }
    } else {
        Modifier.fillMaxWidth()
    }

    Surface(
        modifier = cardModifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (imageUrl.isNotBlank()) {
                RemotePreviewImage(url = imageUrl)
            } else {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title.ifBlank { url },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = site.ifBlank { url },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RemotePreviewImage(url: String) {
    val cached = remember(url) { ChatPreviewImageCache.get(url) }
    val image by produceState<ImageBitmap?>(initialValue = cached?.image, key1 = url) {
        if (cached != null) {
            return@produceState
        }
        value = ChatPreviewImageCache.getOrLoad(url)
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
        )
    }
}

private data class PreviewImageCacheEntry(val image: ImageBitmap?)

private object ChatPreviewImageCache {
    private const val MAX_ENTRIES = 48
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val entries = object : LinkedHashMap<String, ImageBitmap?>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?): Boolean =
            size > MAX_ENTRIES
    }

    fun get(url: String): PreviewImageCacheEntry? = synchronized(lock) {
        if (entries.containsKey(url)) PreviewImageCacheEntry(entries[url]) else null
    }

    fun put(url: String, image: ImageBitmap?) = synchronized(lock) {
        entries[url] = image
    }

    suspend fun getOrLoad(url: String): ImageBitmap? {
        get(url)?.let { cached -> return cached.image }
        var cachedHit = false
        var cachedImage: ImageBitmap? = null
        val deferred = synchronized(lock) {
            if (entries.containsKey(url)) {
                cachedHit = true
                cachedImage = entries[url]
                null
            } else {
                inFlight[url] ?: scope.async { loadPreviewImage(url) }.also { inFlight[url] = it }
            }
        }
        if (cachedHit) return cachedImage
        val loader = deferred ?: return null
        val image = loader.await()
        synchronized(lock) {
            if (inFlight[url] === loader) {
                inFlight.remove(url)
                entries[url] = image
            }
        }
        return image
    }
}

private fun loadPreviewImage(url: String): ImageBitmap? =
    runCatching {
        if (!isSafePreviewImageUrl(url)) return@runCatching null
        val connection = (URL(url).openConnection() as? HttpURLConnection)?.apply {
            instanceFollowRedirects = false
            connectTimeout = 2_500
            readTimeout = 2_500
        } ?: return@runCatching null
        try {
            val responseCode = connection.responseCode
            if (isPreviewRedirectStatus(responseCode) || !isPreviewSuccessStatus(responseCode)) {
                return@runCatching null
            }
            if (!isPreviewImageContentType(connection.contentType)) {
                return@runCatching null
            }
            connection.inputStream.use { input ->
                val bytes = input.readBoundedPreviewBytes() ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@runCatching null
                }
                val decoded = BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = previewImageSampleSize(bounds.outWidth, bounds.outHeight)
                    },
                ) ?: return@runCatching null
                decoded.asImageBitmap()
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

internal fun isSafePreviewImageUrl(url: String): Boolean =
    runCatching {
        val parsed = URL(url.trim())
        parsed.protocol.lowercase() in PREVIEW_IMAGE_URL_SCHEMES &&
            parsed.host.isNotBlank() &&
            parsed.userInfo.isNullOrBlank() &&
            isPublicPreviewImageHost(parsed.host)
    }.getOrDefault(false)

private fun isPublicPreviewImageHost(host: String): Boolean {
    val normalized = host.trim().trim('[', ']').lowercase()
    if (normalized.isBlank()) return false
    if (normalized == "localhost" || normalized.endsWith(".localhost")) return false
    val mappedIpv4 = when {
        normalized.startsWith("::ffff:") -> normalized.removePrefix("::ffff:")
        normalized.startsWith("0:0:0:0:0:ffff:") -> normalized.removePrefix("0:0:0:0:0:ffff:")
        else -> null
    }
    if (mappedIpv4 != null) return isPublicPreviewImageHost(mappedIpv4)
    if (normalized.contains(':')) {
        if (normalized == "::" || normalized == "0:0:0:0:0:0:0:0") return false
        if (normalized == "::1" || normalized == "0:0:0:0:0:0:0:1") return false
        if (normalized.startsWith("fe80:") || normalized.startsWith("fc") || normalized.startsWith("fd")) return false
    }

    val ipv4 = normalized.split('.').mapNotNull { part ->
        part.toIntOrNull()?.takeIf { it in 0..255 }
    }
    if (ipv4.size == 4 && normalized.count { it == '.' } == 3) {
        val first = ipv4[0]
        val second = ipv4[1]
        return when {
            first == 0 -> false
            first == 10 -> false
            first == 127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 168 -> false
            else -> true
        }
    }

    return true
}

internal fun extractChatLinks(text: String): List<String> =
    chatLinkMatches(text).map { it.url }.distinct()

internal fun isOpenableChatUrl(url: String): Boolean =
    runCatching {
        val parsed = URL(url.trim())
        parsed.protocol.lowercase() in CHAT_LINK_URL_SCHEMES &&
            parsed.host.isNotBlank() &&
            parsed.userInfo.isNullOrBlank()
    }.getOrDefault(false)

private fun chatLinkMatches(text: String): List<ChatLinkMatch> =
    CHAT_LINK_PATTERN.findAll(text)
        .mapNotNull { match ->
            val url = match.value.trimEnd(*CHAT_LINK_TRAILING_PUNCTUATION)
            if (url.isBlank() || !isOpenableChatUrl(url)) {
                null
            } else {
                ChatLinkMatch(
                    url = url,
                    start = match.range.first,
                    end = match.range.first + url.length,
                )
            }
        }
        .toList()

private data class ChatLinkMatch(
    val url: String,
    val start: Int,
    val end: Int,
)

internal fun isPreviewRedirectStatus(responseCode: Int): Boolean = responseCode in 300..399

internal fun isPreviewSuccessStatus(responseCode: Int): Boolean = responseCode in 200..299

internal fun isPreviewImageContentType(contentType: String?): Boolean {
    val type = contentType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    return type in PREVIEW_IMAGE_CONTENT_TYPES
}

private fun InputStream.readBoundedPreviewBytes(): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    var read = read(buffer)
    while (read >= 0) {
        total += read
        if (total > MAX_PREVIEW_IMAGE_BYTES) {
            return null
        }
        output.write(buffer, 0, read)
        read = read(buffer)
    }
    return output.toByteArray()
}

private fun previewImageSampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    while (
        width / sampleSize > PREVIEW_IMAGE_TARGET_PX ||
        height / sampleSize > PREVIEW_IMAGE_TARGET_PX
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val MAX_PREVIEW_IMAGE_BYTES = 2 * 1024 * 1024
private const val PREVIEW_IMAGE_TARGET_PX = 192
private const val CHAT_LINK_ANNOTATION_TAG = "CHAT_URL"
private val CHAT_LINK_PATTERN = Regex("""https?://[^\s<>()\[\]{}"']+""", RegexOption.IGNORE_CASE)
private val CHAT_LINK_TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')
private val CHAT_LINK_URL_SCHEMES = setOf("http", "https")
private val PREVIEW_IMAGE_URL_SCHEMES = setOf("http", "https")
private val PREVIEW_IMAGE_CONTENT_TYPES = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
)

@Composable
private fun SollAvatar() {
    Surface(
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "S",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ChatBubbleMeta(
    text: String,
    isUser: Boolean,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = text.ifBlank { "сейчас" },
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    isSending: Boolean,
    isVoiceAvailable: Boolean,
    isVoiceListening: Boolean,
    voicePartialText: String,
    voiceError: String?,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    onDismissVoiceError: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            ChatVoiceStatusRow(
                isListening = isVoiceListening,
                partialText = voicePartialText,
                error = voiceError,
                onDismissError = onDismissVoiceError,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение Soll") },
                    minLines = 1,
                    maxLines = 4,
                    enabled = !isSending,
                    shape = RoundedCornerShape(22.dp),
                )
                val canUseVoice = isVoiceAvailable && !isSending
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canUseVoice, onClick = onVoiceClick),
                    color = when {
                        isVoiceListening -> MaterialTheme.colorScheme.error
                        canUseVoice -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = when {
                        isVoiceListening -> MaterialTheme.colorScheme.onError
                        canUseVoice -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isVoiceListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isVoiceListening) {
                                "Остановить диктовку"
                            } else {
                                "Надиктовать сообщение"
                            },
                        )
                    }
                }
                val canSend = value.isNotBlank() && !isSending
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canSend, onClick = onSend),
                    color = if (canSend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (canSend) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatVoiceStatusRow(
    isListening: Boolean,
    partialText: String,
    error: String?,
    onDismissError: () -> Unit,
) {
    val text = when {
        error != null -> error
        isListening && partialText.isNotBlank() -> "Слушаю: $partialText"
        isListening -> "Слушаю. Говорите сообщение для Soll."
        else -> null
    } ?: return

    val color = if (error != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (error != null) Icons.Default.ErrorOutline else Icons.Default.Mic,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (error != null) {
            TextButton(onClick = onDismissError) {
                Text("ОК")
            }
        }
    }
}

private fun chatSubtitle(uiState: ChatUiState): String =
    when {
        uiState.error != null -> "сервер требует проверки"
        uiState.encrypted -> "зашифрованный канал"
        uiState.messages.isNotEmpty() -> "сессия ${uiState.sessionId}"
        else -> "ожидание соединения"
    }

private fun friendlyChatError(error: String): Pair<String, String> =
    when {
        error.contains("SCHEMA_NOT_READY", ignoreCase = true) ||
            error.contains("missing_tables", ignoreCase = true) ||
            error.contains("soll_chat_", ignoreCase = true) -> {
            "Схема чата Soll не готова" to "На Yii2-сервере не хватает chat-таблиц. Примени вторую миграцию Soll и сбрось schema cache."
        }
        error.contains("503", ignoreCase = true) || error.contains("DATABASE_UNAVAILABLE", ignoreCase = true) -> {
            "API Soll не готов" to "Сервер отвечает 503: проверь db_soll, миграции Soll и schema cache на Yii2."
        }
        error.contains("404", ignoreCase = true) -> {
            "API чата не найден" to "Сервер отвечает, но endpoint /api/v1/soll/chat/* еще не опубликован."
        }
        error.contains("401", ignoreCase = true) || error.contains("token", ignoreCase = true) -> {
            "Нужен доступ к серверу" to "Проверь Bearer-токен или pairing устройства в настройках Soll."
        }
        else -> {
            "Сервер Soll недоступен" to error
        }
    }

internal fun messageTitle(message: SollChatMessage): String? {
    if (message.isFromUser) return null
    val title = message.metadata["title"]?.toString()?.trim().orEmpty()
    val source = messageSourceLabel(message)
    val resolvedTitle = title.ifBlank { "Soll" }
    return source
        ?.takeIf { !it.equals(resolvedTitle, ignoreCase = true) }
        ?.let { "$resolvedTitle · $it" }
        ?: resolvedTitle
}

internal fun messageSourceLabel(message: SollChatMessage): String? {
    val source = message.metadata["source"]?.toString()?.trim().orEmpty()
    return when (source) {
        "telegram_mirror" -> "Telegram"
        "task_intake" -> "Задачи"
        "android_action" -> null
        "android_app" -> "Android"
        "desktop" -> "Desktop"
        "server" -> null
        "yii2_soll_api" -> "API"
        else -> source.ifBlank { null }
    }
}

internal data class ChatBadgeUi(
    val text: String,
    val kind: ChatBadgeKind,
)

private data class ChatBadgeStyle(
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
)

internal enum class ChatBadgeKind {
    STATUS,
    SECURITY,
    TASK,
    SOURCE,
    INFO,
    SUCCESS,
    WARNING,
    DANGER,
}

internal fun SollChatMessage.badgeUis(): List<ChatBadgeUi> = buildList {
    metadata["status"]
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?.let { status -> add(ChatBadgeUi(text = status, kind = ChatBadgeKind.STATUS)) }
    metadata["badges"].asBadgeMaps().forEach { badge ->
        badge.toChatBadgeUiOrNull()?.let(::add)
    }
    if (metadata["encrypted"] == true) add(ChatBadgeUi(text = "AES", kind = ChatBadgeKind.SECURITY))
    if (metadata["task_intake"] != null) add(ChatBadgeUi(text = "task", kind = ChatBadgeKind.TASK))
    messageSourceLabel(this@badgeUis)?.let { source ->
        add(ChatBadgeUi(text = source, kind = ChatBadgeKind.SOURCE))
    }
}.distinct()

private fun Any?.asBadgeMaps(): List<Map<*, *>> =
    (this as? List<*>)
        ?.mapNotNull { item -> item as? Map<*, *> }
        .orEmpty()

private fun Map<*, *>.toChatBadgeUiOrNull(): ChatBadgeUi? {
    val text = this["label"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        ?: this["text"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        ?: return null
    val tone = this["tone"]?.toString()?.trim().orEmpty()
    val kind = when (tone.lowercase()) {
        "success", "ok", "done" -> ChatBadgeKind.SUCCESS
        "warning", "warn", "attention" -> ChatBadgeKind.WARNING
        "danger", "error", "failed" -> ChatBadgeKind.DANGER
        "info", "source" -> ChatBadgeKind.INFO
        else -> ChatBadgeKind.SOURCE
    }
    return ChatBadgeUi(text = text, kind = kind)
}

private fun SollChatMessage.linkPreviewOrNull(): Map<*, *>? {
    val direct = metadata["link_preview"] as? Map<*, *>
    if (direct != null && direct.isNotEmpty()) return direct
    val taskIntake = metadata["task_intake"] as? Map<*, *>
    return taskIntake?.get("link_preview") as? Map<*, *>
}
