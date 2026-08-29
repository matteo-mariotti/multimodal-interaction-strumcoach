package com.example.strumcoach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun ExercisesScreen(
    exercises: List<Exercise>,
    selectedExerciseId: String,
    onExerciseSelected: (String) -> Unit,
    onAddExercise: (String, Boolean, String, String) -> Unit,
    onUpdateExercise: (Exercise, String, Boolean, String, String) -> Unit,
    onPublish: (Exercise) -> Unit,
    onDelete: (Exercise) -> Unit,
    onGoToCommunity: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    currentlyPlayingUrl: String?,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (exercises.isEmpty()) {
                EmptyLibraryState(
                    message = "Your library is empty. Create your first exercise or explore the community!",
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
                            text = "Your Exercises",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }
                    items(exercises) { exercise ->
                        ExerciseM3Card(
                            exercise = exercise,
                            isSelected = exercise.id == selectedExerciseId,
                            onClick = { onExerciseSelected(exercise.id) },
                            onPublish = { onPublish(exercise) },
                            onEdit = { editingExercise = exercise },
                            onDelete = { onDelete(exercise) },
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
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            }

            if (showCreateDialog || editingExercise != null) {
                CreateExerciseDialog(
                    initialIsSong = false,
                    exerciseToEdit = editingExercise,
                    onDismiss = {
                        showCreateDialog = false
                        editingExercise = null
                    },
                    onConfirm = { name, isSong, pattern, difficulty ->
                        val toEdit = editingExercise
                        if (toEdit != null) {
                            onUpdateExercise(toEdit, name, isSong, pattern, difficulty)
                        } else {
                            onAddExercise(name, isSong, pattern, difficulty)
                        }
                        showCreateDialog = false
                        editingExercise = null
                    }
                )
            }
        }
    }
}

@Composable
fun ExerciseM3Card(
    exercise: Exercise,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPublish: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    currentlyPlayingUrl: String?
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )

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

                if (exercise.hasReference && !exercise.isPublic) {
                    IconButton(onClick = onPublish) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Publish", tint = MaterialTheme.colorScheme.secondary)
                    }
                }

                // Only the creator of a locally-authored exercise can edit it. Checked via
                // communitySourceId (set only on import) rather than authorName == "Me",
                // since publishing overwrites authorName with the creator's display name
                // but leaves communitySourceId null on your own exercise.
                if (exercise.communitySourceId == null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SurfaceTag(text = exercise.difficulty, color = getDifficultyColor(exercise.difficulty))

            if (exercise.strummingPattern.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                StrummingPatternView(pattern = exercise.strummingPattern)
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
