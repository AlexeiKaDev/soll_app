package com.soll.presentation.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class Tool(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToBookReader: () -> Unit,
    onNavigateToBreathing: () -> Unit,
    onNavigateToCourseCoach: () -> Unit,
) {
    val tools = listOf(
        Tool(
            id = "course_coach",
            name = "Курс",
            description = "Ежедневная программа, напоминания и прогресс по курсу",
            icon = Icons.Default.AutoAwesome
        ),
        Tool(
            id = "book_reader",
            name = "Чтение книг",
            description = "Чтение EPUB и озвучивание текста через TTS",
            icon = Icons.Default.Book
        ),
        Tool(
            id = "guided_breathing",
            name = "Дыхание",
            description = "3 раунда дыхания: дыхание, задержка, восстановление",
            icon = Icons.Default.Air
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инструменты") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tools) { tool ->
                ToolItem(
                    tool = tool,
                    onClick = {
                        when (tool.id) {
                            "course_coach" -> onNavigateToCourseCoach()
                            "book_reader" -> onNavigateToBookReader()
                            "guided_breathing" -> onNavigateToBreathing()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolItem(
    tool: Tool,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
