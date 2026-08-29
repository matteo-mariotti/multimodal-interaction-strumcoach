package com.example.strumcoach.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.strumcoach.SessionStats
import com.example.strumcoach.StrumAnalyzer
import com.example.strumcoach.ui.theme.StrumDown
import com.example.strumcoach.ui.theme.StrumUp
import com.example.strumcoach.ui.theme.Warning

@Composable
fun SessionReportScreen(
    stats: SessionStats,
    onRepeat: () -> Unit,
    onDismiss: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    currentlyPlayingUrl: String?
) {
    var showFullscreenGraph by remember { mutableStateOf(false) }

    if (showFullscreenGraph) {
        FullscreenGraphDialog(
            stats = stats,
            onDismiss = { showFullscreenGraph = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = if (stats.isReference) "Reference Recorded" else "Session Report",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(40.dp))

            if (!stats.isReference) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                    CircularProgressIndicator(
                        progress = { stats.accuracy / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 16.dp,
                        color = getAccuracyColor(stats.accuracy),
                        trackColor = getAccuracyColor(stats.accuracy).copy(alpha = 0.1f)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${stats.accuracy}%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                        Surface(
                            color = getAccuracyColor(stats.accuracy),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = stats.grade,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Strum Analysis", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showFullscreenGraph = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Expand")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    val reportMaxLen = stats.referenceSignal?.size?.coerceAtLeast(stats.rawSignal.size) ?: stats.rawSignal.size
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        StrumMarkersTimeline(
                            strums = stats.detectedStrums,
                            referenceStrums = stats.referenceStrums,
                            totalLength = reportMaxLen,
                            indexShift = stats.indexShift,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        StrumWaveformGraph(
                            signal = stats.rawSignal,
                            strums = stats.detectedStrums,
                            referenceSignal = stats.referenceSignal,
                            referenceStrums = stats.referenceStrums,
                            audioEnvelope = stats.audioEnvelope,
                            indexShift = stats.indexShift,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    GraphLegend()
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!stats.isReference) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "Rhythm & Dynamics", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Warning)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = stats.dynamicsFeedback, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        stats.audioUrl?.let { url ->
            if (url.isNotEmpty()) {
                item {
                    AudioPlaybackRow(
                        url = url,
                        label = "Your Execution",
                        onPlay = onPlayAudio,
                        onStop = onStopAudio,
                        isPlaying = currentlyPlayingUrl == url
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        stats.referenceAudioUrl?.let { url ->
            if (url.isNotEmpty() && !stats.isReference) {
                item {
                    AudioPlaybackRow(
                        url = url,
                        label = "Reference Goal",
                        onPlay = onPlayAudio,
                        onStop = onStopAudio,
                        isPlaying = currentlyPlayingUrl == url
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (stats.debugInfo.isNotEmpty()) {
            item {
                DebugInfoSection(debugInfo = stats.debugInfo)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        item {
            Button(
                onClick = onRepeat,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("REPEAT EXERCISE")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("DISMISS")
            }
        }
    }
}

@Composable
fun AudioPlaybackRow(
    url: String,
    label: String,
    onPlay: (String) -> Unit,
    onStop: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) onStop() else onPlay(url)
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DebugInfoSection(debugInfo: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Algorithm Diagnostics", style = MaterialTheme.typography.titleSmall)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                debugInfo.forEach { (key, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun GraphLegend(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(color = MaterialTheme.colorScheme.primary, label = "Gyroscope", isCircle = false)
            LegendItem(color = MaterialTheme.colorScheme.tertiary, label = "Audio Volume", isCircle = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), label = "Reference", isCircle = false)
            LegendItem(color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), label = "Threshold", isCircle = false, isDashed = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(color = StrumDown, label = "Down Strum", isCircle = true)
            LegendItem(color = StrumUp, label = "Up Strum", isCircle = true)
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, isCircle: Boolean, isDashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isCircle) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        } else {
            if (isDashed) {
                Canvas(modifier = Modifier.size(width = 20.dp, height = 2.dp)) {
                    drawLine(
                        color = color,
                        start = Offset(0f, center.y),
                        end = Offset(size.width, center.y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    )
                }
            } else {
                Box(modifier = Modifier.size(width = 20.dp, height = 2.dp).background(color))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun FullscreenGraphDialog(stats: SessionStats, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Detailed Analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = stats.exerciseName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = rememberScrollState()
                    val minStepX = 12.dp
                    val maxLen = stats.referenceSignal?.size?.coerceAtLeast(stats.rawSignal.size) ?: stats.rawSignal.size
                    val totalWidth = minStepX * maxLen

                    Box(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
                        StrumWaveformGraph(
                            signal = stats.rawSignal,
                            strums = stats.detectedStrums,
                            referenceSignal = stats.referenceSignal,
                            referenceStrums = stats.referenceStrums,
                            audioEnvelope = stats.audioEnvelope,
                            indexShift = stats.indexShift,
                            minStepX = minStepX,
                            // Gyro-only fallback fired (no flux threshold) whenever no audio onset
                            // level is recorded for the session.
                            threshold = if (stats.audioOnsetThreshold == null) {
                                StrumAnalyzer.GYRO_STRUM_THRESHOLD
                            } else {
                                null
                            },
                            audioThreshold = stats.audioOnsetThreshold,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(totalWidth)
                                .padding(vertical = 32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    GraphLegend(modifier = Modifier.padding(20.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scroll horizontally to analyze the entire session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
