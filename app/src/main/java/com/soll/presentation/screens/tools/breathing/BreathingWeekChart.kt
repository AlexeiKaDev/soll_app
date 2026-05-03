package com.soll.presentation.screens.tools.breathing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.soll.domain.breathing.BreathingWeekDayStat

/** Столбики по дням в духе экранов «streak / practice» (Duolingo-подобная подача). */
@Composable
fun BreathingWeekChart(
    days: List<BreathingWeekDayStat>,
    modifier: Modifier = Modifier,
) {
    val maxMinutes = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1f) ?: 1f
    val barAreaHeight = 132.dp
    val plotHeight = barAreaHeight - 36.dp
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val onInactive = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Минуты за 7 дней",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { day ->
                val fraction = (day.minutes / maxMinutes).coerceIn(0f, 1f)
                val filled = day.minutes > 0.05f
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(plotHeight),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val rawH = plotHeight * fraction
                        val barH = rawH.coerceAtLeast(if (filled) 10.dp else 4.dp).coerceAtMost(plotHeight)
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(barH)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (filled) primary else inactive.copy(alpha = 0.55f))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (filled) primary else onInactive
                    )
                    Text(
                        text = "${day.minutes.toInt()}м",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
