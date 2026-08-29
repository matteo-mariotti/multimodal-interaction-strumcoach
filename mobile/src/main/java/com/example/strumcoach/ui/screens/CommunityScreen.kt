package com.example.strumcoach.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.strumcoach.Exercise
import com.example.strumcoach.ui.theme.Success
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    exercises: List<Exercise>,
    userLibrary: List<Exercise>,
    currentUserId: String,
    onDownload: (Exercise) -> Unit,
    onDelete: (Exercise) -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    currentlyPlayingUrl: String?,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = exercises.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.authorName.contains(searchQuery, ignoreCase = true)
    }

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
                    text = "Community Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by name or author...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            items(filtered) { exercise ->
                val isInLibrary = userLibrary.any { it.communitySourceId == exercise.id || it.id == exercise.id }
                CommunityExerciseCard(
                    exercise = exercise,
                    isInLibrary = isInLibrary,
                    isOwner = (exercise.userId == currentUserId && currentUserId.isNotEmpty()),
                    onDownload = onDownload,
                    onDelete = onDelete,
                    onPlayAudio = onPlayAudio,
                    onStopAudio = onStopAudio,
                    currentlyPlayingUrl = currentlyPlayingUrl
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun CommunityExerciseCard(
    exercise: Exercise,
    isInLibrary: Boolean,
    isOwner: Boolean,
    onDownload: (Exercise) -> Unit,
    onDelete: (Exercise) -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    currentlyPlayingUrl: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "By: ${exercise.authorName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }

                if (!exercise.referenceAudioUrl.isNullOrEmpty()) {
                    val isPlaying = currentlyPlayingUrl == exercise.referenceAudioUrl
                    IconButton(onClick = {
                        if (isPlaying) onStopAudio() else onPlayAudio(exercise.referenceAudioUrl)
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Listen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isOwner) {
                    IconButton(onClick = { onDelete(exercise) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }

                if (isInLibrary) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "In Library",
                        tint = Success,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    IconButton(onClick = { onDownload(exercise) }) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SurfaceTag(text = exercise.difficulty, color = getDifficultyColor(exercise.difficulty))
                if (exercise.isSong) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SurfaceTag(text = "Song", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}
