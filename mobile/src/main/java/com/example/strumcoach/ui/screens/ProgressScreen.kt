package com.example.strumcoach.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.strumcoach.SessionStats
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    sessions: List<SessionStats>,
    onSessionClick: (SessionStats) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val executions = remember(sessions) { sessions.filter { !it.isReference } }
    val recordings = remember(sessions) { sessions.filter { it.isReference } }
    var selectedTab by remember { mutableIntStateOf(0) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)
        ) {
            item {
                Text(
                    text = "Your Progress",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(text = "Accuracy Trend", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(20.dp))
                        val chartData = if (executions.isEmpty()) listOf(0f)
                            else executions.takeLast(10).map { it.accuracy.toFloat() }.reversed()
                        AccuracyChart(
                            data = chartData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        listOf("Executions", "Recordings").forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = index }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val activeList = if (selectedTab == 0) executions else recordings
            val emptyText = if (selectedTab == 0)
                "No sessions recorded yet. Keep practicing!"
            else
                "No reference recordings yet."

            if (activeList.isEmpty()) {
                item {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(activeList) { session ->
                SessionHistoryCard(
                    session = session,
                    showScore = selectedTab == 0,
                    onClick = { onSessionClick(session) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SessionHistoryCard(session: SessionStats, showScore: Boolean = true, onClick: () -> Unit) {
    val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        .format(Date(session.id))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.exerciseName, style = MaterialTheme.typography.titleMedium)
                Text(text = date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (showScore) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = getAccuracyColor(session.accuracy).copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${session.accuracy}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = getAccuracyColor(session.accuracy)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Reference recording",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AccuracyChart(data: List<Float>, modifier: Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = labelColor
    )

    Canvas(modifier = modifier) {
        val axisPadding = 32.dp.toPx()
        val chartWidth = size.width - axisPadding
        val height = size.height
        val spacing = chartWidth / (data.size - 1).coerceAtLeast(1)

        // Draw grid lines and labels
        val labels = listOf("100", "75", "50", "25", "0")
        for (i in 0..4) {
            val y = height * i / 4
            
            // Draw axis label
            val textLayoutResult = textMeasurer.measure(labels[i], labelStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    x = (axisPadding - textLayoutResult.size.width) / 2f - 4.dp.toPx(),
                    y = y - textLayoutResult.size.height / 2f
                )
            )

            drawLine(
                color = Color.Gray.copy(alpha = 0.1f),
                start = Offset(axisPadding, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (data.size > 1) {
            for (i in 0 until data.size - 1) {
                val startX = axisPadding + i * spacing
                val startY = height - (data[i] / 100f * height)
                val endX = axisPadding + (i + 1) * spacing
                val endY = height - (data[i + 1] / 100f * height)

                drawLine(
                    color = primaryColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = Offset(startX, startY)
                )
            }
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(size.width, height - (data.last() / 100f * height))
            )
        } else if (data.size == 1) {
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(axisPadding, height - (data[0] / 100f * height))
            )
        }
    }
}
