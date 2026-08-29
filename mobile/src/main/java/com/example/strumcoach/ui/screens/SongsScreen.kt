package com.example.strumcoach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.strumcoach.Exercise
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    songs: List<Exercise>,
    selectedExerciseId: String,
    onSongSelected: (String) -> Unit,
    onAddSong: (String, Boolean, String, String) -> Unit,
    onUpdateSong: (Exercise, String, Boolean, String, String) -> Unit,
    onDelete: (Exercise) -> Unit,
    onGoToCommunity: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    currentlyPlayingUrl: String?,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingSong by remember { mutableStateOf<Exercise?>(null) }
    val filteredSongs = songs.filter { it.name.contains(searchQuery, ignoreCase = true) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (songs.isEmpty() && searchQuery.isEmpty()) {
                EmptyLibraryState(
                    message = "You haven't saved any songs yet. Start now!",
                    onCreateNew = { showCreateDialog = true },
                    onExploreCommunity = onGoToCommunity
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 80.dp)
                ) {
                    item {
                        Text(
                            text = "Your Songs",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search a song...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    items(filteredSongs) { song ->
                        SongCard(
                            song = song,
                            isSelected = song.id == selectedExerciseId,
                            onClick = { onSongSelected(song.id) },
                            onEdit = { editingSong = song },
                            onDelete = { onDelete(song) },
                            onPlayAudio = onPlayAudio,
                            onStopAudio = onStopAudio,
                            currentlyPlayingUrl = currentlyPlayingUrl
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Song")
            }

            if (showCreateDialog || editingSong != null) {
                CreateExerciseDialog(
                    initialIsSong = true,
                    exerciseToEdit = editingSong,
                    onDismiss = {
                        showCreateDialog = false
                        editingSong = null
                    },
                    onConfirm = { name, isSong, pattern, difficulty ->
                        val toEdit = editingSong
                        if (toEdit != null) {
                            onUpdateSong(toEdit, name, isSong, pattern, difficulty)
                        } else {
                            onAddSong(name, isSong, pattern, difficulty)
                        }
                        showCreateDialog = false
                        editingSong = null
                    }
                )
            }
        }
    }
}

@Composable
fun SongCard(
    song: Exercise,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    currentlyPlayingUrl: String?
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                             else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(enabled = isSelected)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SurfaceTag(text = song.difficulty, color = getDifficultyColor(song.difficulty))
            }

            if (!song.referenceAudioUrl.isNullOrEmpty()) {
                val isPlaying = currentlyPlayingUrl == song.referenceAudioUrl
                IconButton(onClick = {
                    if (isPlaying) onStopAudio() else onPlayAudio(song.referenceAudioUrl)
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Listen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (song.communitySourceId == null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            }
        }
    }
}
