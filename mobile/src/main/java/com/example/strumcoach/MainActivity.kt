package com.example.strumcoach

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.strumcoach.ui.screens.CommunityScreen
import com.example.strumcoach.ui.screens.DashboardScreen
import com.example.strumcoach.ui.screens.LoginScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.example.strumcoach.ui.screens.ExercisesScreen
import com.example.strumcoach.ui.screens.ProgressScreen
import com.example.strumcoach.ui.Screen
import com.example.strumcoach.ui.screens.SessionReportScreen
import com.example.strumcoach.ui.screens.SongsScreen
import com.example.strumcoach.ui.navItems
import com.example.strumcoach.ui.theme.StrumCoachTheme
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            StrumCoachTheme {
                val viewModel: StrumCoachMobileViewModel = viewModel()

                val signInLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        account.idToken?.let { viewModel.firebaseAuthWithGoogle(it) }
                    } catch (e: ApiException) {
                        android.util.Log.e("MainActivity", "Google sign-in failed", e)
                    }
                }

                if (viewModel.currentUser == null) {
                    LoginScreen(onSignIn = { signInLauncher.launch(googleSignInClient.signInIntent) })
                } else {

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current

                // Observe error events from ViewModel
                LaunchedEffect(viewModel.errorEvents) {
                    viewModel.errorEvents.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                // Permessi audio per la registrazione
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Audio permission is required for sessions")
                        }
                    }
                }

                SideEffect {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                var showConfirmDialog by remember { mutableStateOf<Exercise?>(null) }
                var isCreationMode by remember { mutableStateOf(false) }
                var showSetCreatorNameDialog by remember { mutableStateOf<Exercise?>(null) }
                var showDeleteCommunityDialog by remember { mutableStateOf<Exercise?>(null) }
                var showDeleteLibraryDialog by remember { mutableStateOf<Exercise?>(null) }

                // Mostra i risulati al termine della sessione
                LaunchedEffect(viewModel.currentSessionReport?.id) {
                    if (viewModel.currentSessionReport != null && currentRoute != Screen.SessionReport.route) {
                        navController.navigate(Screen.SessionReport.route)
                    }
                }

                if (showConfirmDialog != null) {
                    val currentExercise = showConfirmDialog!!
                    ConfirmationDialog(
                        exercise = currentExercise,
                        isCreationMode = isCreationMode,
                        onConfirm = {
                            viewModel.iniziaEsercizio(currentExercise.id)
                            showConfirmDialog = null
                        },
                        onRecordReference = {
                            viewModel.startReferenceRecording(currentExercise.id)
                            showConfirmDialog = null
                        },
                        onDismiss = { showConfirmDialog = null }
                    )
                }

                if (showDeleteCommunityDialog != null) {
                    AlertDialog(
                        onDismissRequest = { showDeleteCommunityDialog = null },
                        title = { Text("Remove from Community") },
                        text = { Text("Do you want to delete this exercise from the community?") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.deleteFromCommunity(showDeleteCommunityDialog!!)
                                showDeleteCommunityDialog = null
                            }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteCommunityDialog = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (showDeleteLibraryDialog != null) {
                    val exercise = showDeleteLibraryDialog!!
                    AlertDialog(
                        onDismissRequest = { showDeleteLibraryDialog = null },
                        title = { Text("Delete Exercise") },
                        text = { Text("Are you sure you want to delete '${exercise.name}' from your library?") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.eliminaEsercizio(exercise)
                                showDeleteLibraryDialog = null
                            }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteLibraryDialog = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            if (currentRoute != Screen.SessionReport.route) {
                                TopAppBar(
                                    title = { 
                                        Text(
                                            "StrumCoach",
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    actions = {
                                        WatchStatusIndicator(
                                            isConnected = viewModel.isWatchConnected,
                                            watchState = viewModel.watchScreen,
                                            onPing = viewModel::ping,
                                            onStop = viewModel::terminaEsercizio,
                                            onOpenWatch = viewModel::openWatchApp
                                        )
                                        IconButton(onClick = {
                                            viewModel.signOut()
                                            googleSignInClient.signOut()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Logout,
                                                contentDescription = "Sign out"
                                            )
                                        }
                                    }
                                )
                            }
                        },
                        bottomBar = {
                            if (currentRoute != Screen.SessionReport.route) {
                                Column {
                                    if (viewModel.currentlyPlayingUrl != null) {
                                        AudioPlaybackBar(
                                            progress = viewModel.playbackProgress,
                                            label = viewModel.playbackLabel,
                                            onStop = viewModel::stopPlayback
                                        )
                                    }
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp
                                    ) {
                                        navItems.forEach { screen ->
                                            NavigationBarItem(
                                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                                label = { Text(screen.title) },
                                                selected = currentRoute == screen.route,
                                                onClick = {
                                                    viewModel.refreshData()
                                                    if (currentRoute != screen.route) {
                                                        navController.navigate(screen.route) {
                                                            popUpTo(navController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Dashboard.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Screen.Dashboard.route) {
                                val lastEx = viewModel.findExercise(viewModel.lastExerciseId)
                                    ?: viewModel.sessionHistory.firstOrNull()?.let { viewModel.findExercise(it.exerciseId) }
                                    ?: viewModel.exercises.firstOrNull()
                                    ?: viewModel.songs.firstOrNull()

                                DashboardScreen(
                                    name = viewModel.currentUser?.displayName?.substringBefore(" ") ?: "User",
                                    battery = viewModel.watchBatteryLevel,
                                    lastExercise = lastEx?.name ?: "No exercises yet",
                                    isLastExerciseValid = lastEx?.hasReference == true || lastEx == null,
                                    minutes = viewModel.weeklyMinutes,
                                    accuracy = viewModel.avgAccuracy,
                                    onQuickStart = {
                                        if (viewModel.isWatchConnected) {
                                            var last = viewModel.findExercise(viewModel.lastExerciseId)
                                            
                                            if (last == null) {
                                                last = viewModel.sessionHistory.firstOrNull()?.let { viewModel.findExercise(it.exerciseId) }
                                                    ?: viewModel.exercises.firstOrNull()
                                                    ?: viewModel.songs.firstOrNull()
                                            }

                                            if (last != null) {
                                                isCreationMode = false
                                                showConfirmDialog = last
                                            } else {
                                                navController.navigate(Screen.Exercises.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Connect watch to start exercise")
                                            }
                                        }
                                    },
                                    isRefreshing = viewModel.isRefreshing,
                                    onRefresh = viewModel::refreshData
                                )
                            }
                            composable(Screen.Exercises.route) {
                                ExercisesScreen(
                                    exercises = viewModel.exercises,
                                    selectedExerciseId = viewModel.lastExerciseId,
                                    onExerciseSelected = { id: String ->
                                        if (viewModel.isWatchConnected) {
                                            isCreationMode = false
                                            showConfirmDialog = viewModel.findExercise(id)
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Connect watch to start exercise")
                                            }
                                        }
                                    },
                                    onAddExercise = { name, isSong, pattern, difficulty ->
                                        val color = when (difficulty) {
                                            "Easy" -> Color(0xFF4CAF50)
                                            "Medium" -> Color(0xFFFFC107)
                                            "Hard" -> Color(0xFFF44336)
                                            else -> Color(0xFF2196F3)
                                        }
                                        viewModel.addNewExercise(name, difficulty, color, isSong, pattern) { newEx ->
                                            isCreationMode = true
                                            showConfirmDialog = newEx
                                        }
                                    },
                                    onUpdateExercise = { exercise, name, isSong, pattern, difficulty ->
                                        val color = when (difficulty) {
                                            "Easy" -> Color(0xFF4CAF50)
                                            "Medium" -> Color(0xFFFFC107)
                                            "Hard" -> Color(0xFFF44336)
                                            else -> Color(0xFF2196F3)
                                        }
                                        viewModel.updateExercise(exercise, name, difficulty, color, isSong, pattern)
                                    },
                                    onPublish = { exercise ->
                                        if (viewModel.creatorName.isEmpty()) {
                                            showSetCreatorNameDialog = exercise
                                        } else {
                                            viewModel.pubblica(exercise) { success ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        if (success) "Exercise published!" else "Error during publication"
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onDelete = { showDeleteLibraryDialog = it },
                                    onGoToCommunity = {
                                        navController.navigate(Screen.Community.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onPlayAudio = viewModel::togglePlayback,
                                    onStopAudio = viewModel::stopPlayback,
                                    currentlyPlayingUrl = viewModel.currentlyPlayingUrl,
                                    isRefreshing = viewModel.isRefreshing,
                                    onRefresh = viewModel::refreshData
                                )
                            }
                            composable(Screen.Songs.route) {
                                SongsScreen(
                                    songs = viewModel.songs,
                                    selectedExerciseId = viewModel.lastExerciseId,
                                    onSongSelected = { id: String ->
                                        if (viewModel.isWatchConnected) {
                                            isCreationMode = false
                                            showConfirmDialog = viewModel.findExercise(id)
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Connect watch to start exercise")
                                            }
                                        }
                                    },
                                    onAddSong = { name, isSong, pattern, difficulty ->
                                        val color = when (difficulty) {
                                            "Easy" -> Color(0xFF4CAF50)
                                            "Medium" -> Color(0xFFFFC107)
                                            "Hard" -> Color(0xFFF44336)
                                            else -> Color(0xFF2196F3)
                                        }
                                        viewModel.addNewExercise(name, difficulty, color, isSong, pattern) { newEx ->
                                            isCreationMode = true
                                            showConfirmDialog = newEx
                                        }
                                    },
                                    onUpdateSong = { exercise, name, isSong, pattern, difficulty ->
                                        val color = when (difficulty) {
                                            "Easy" -> Color(0xFF4CAF50)
                                            "Medium" -> Color(0xFFFFC107)
                                            "Hard" -> Color(0xFFF44336)
                                            else -> Color(0xFF2196F3)
                                        }
                                        viewModel.updateExercise(exercise, name, difficulty, color, isSong, pattern)
                                    },
                                    onDelete = { showDeleteLibraryDialog = it },
                                    onGoToCommunity = {
                                        navController.navigate(Screen.Community.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onPlayAudio = viewModel::togglePlayback,
                                    onStopAudio = viewModel::stopPlayback,
                                    currentlyPlayingUrl = viewModel.currentlyPlayingUrl,
                                    isRefreshing = viewModel.isRefreshing,
                                    onRefresh = viewModel::refreshData
                                )
                            }
                            composable(Screen.Community.route) {
                                CommunityScreen(
                                    exercises = viewModel.communityExercises,
                                    userLibrary = viewModel.exercises + viewModel.songs,
                                    currentUserId = viewModel.currentUser!!.uid,
                                    onDownload = { exercise ->
                                        viewModel.downloadFromCommunity(exercise) { _, message ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(message)
                                            }
                                        }
                                    },
                                    onDelete = { exercise ->
                                        showDeleteCommunityDialog = exercise
                                    },
                                    onPlayAudio = viewModel::togglePlayback,
                                    onStopAudio = viewModel::stopPlayback,
                                    currentlyPlayingUrl = viewModel.currentlyPlayingUrl,
                                    isRefreshing = viewModel.isRefreshing,
                                    onRefresh = viewModel::refreshData
                                )
                            }
                            composable(Screen.Progress.route) {
                                ProgressScreen(
                                    sessions = viewModel.sessionHistory,
                                    onSessionClick = { session ->
                                        viewModel.showSessionReport(session)
                                        navController.navigate(Screen.SessionReport.route)
                                    },
                                    isRefreshing = viewModel.isRefreshing,
                                    onRefresh = viewModel::refreshData
                                )
                            }
                            composable(Screen.SessionReport.route) {
                                val stats = viewModel.currentSessionReport
                                if (stats != null) {
                                    SessionReportScreen(
                                        stats = stats,
                                        onRepeat = {
                                            if (viewModel.isWatchConnected) {
                                                viewModel.iniziaEsercizio(stats.exerciseId)
                                                navController.popBackStack()
                                                viewModel.clearReport()
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Connect watch to start exercise")
                                                }
                                            }
                                        },
                                        onDismiss = {
                                            navController.popBackStack()
                                            viewModel.clearReport()
                                        },
                                        onPlayAudio = viewModel::togglePlayback,
                                        onStopAudio = viewModel::stopPlayback,
                                        currentlyPlayingUrl = viewModel.currentlyPlayingUrl
                                    )
                                }
                            }
                        }
                    }

                    if (viewModel.watchScreen == "EXECUTION" || viewModel.watchScreen == "COUNTDOWN" || viewModel.watchScreen == "READY_CHECK" || viewModel.isAnalyzing) {
                        val currentExercise = viewModel.findExercise(viewModel.lastExerciseId)
                        
                        var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(key1 = viewModel.watchScreen, key2 = viewModel.isAnalyzing) {
                            while (true) {
                                currentTime = System.currentTimeMillis()
                                kotlinx.coroutines.delay(100)
                            }
                        }

                        val elapsed = if (viewModel.sessionStartTime > 0) currentTime - viewModel.sessionStartTime else 0L
                        val remaining = if (viewModel.sessionDurationMs > 0 && viewModel.watchScreen == "EXECUTION" && !viewModel.isAnalyzing) {
                            (viewModel.sessionDurationMs - elapsed).coerceAtLeast(0L)
                        } else null
                        
                        ActiveSessionOverlay(
                            exerciseName = currentExercise?.name ?: "Exercise",
                            remainingTimeMs = remaining,
                            elapsedTimeMs = if (remaining == null && !viewModel.isAnalyzing && !viewModel.watchScreen.isEmpty()) elapsed else null,
                            isReadyCheck = viewModel.watchScreen == "READY_CHECK",
                            isCountdown = viewModel.watchScreen == "COUNTDOWN",
                            countdownValue = viewModel.watchCountdown,
                            isAnalyzing = viewModel.isAnalyzing,
                            onStop = viewModel::terminaEsercizio
                        )
                    }
                }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Wearable.getCapabilityClient(this)
            .addLocalCapability("strum_coach_mobile_active")
    }

    override fun onStop() {
        super.onStop()
        Wearable.getCapabilityClient(this)
            .removeLocalCapability("strum_coach_mobile_active")
    }
}

@Composable
fun AudioPlaybackBar(
    progress: Float,
    label: String,
    onStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop playback",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WatchStatusIndicator(
    isConnected: Boolean,
    watchState: String,
    onPing: () -> Unit,
    onStop: () -> Unit,
    onOpenWatch: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        if (isConnected) {
            if (watchState == "EXECUTION") {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onPing) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Ping",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            IconButton(
                onClick = onOpenWatch,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.errorContainer
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Watch,
                    contentDescription = "Watch",
                    modifier = Modifier.size(18.dp),
                    tint = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer 
                           else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color(0xFF4CAF50) else Color.Red)
            )
        }
    }
}

@Composable
fun ConfirmationDialog(
    exercise: Exercise,
    isCreationMode: Boolean,
    onConfirm: () -> Unit,
    onRecordReference: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Start Session") },
        text = {
            Column {
                Text(text = "Exercise: ${exercise.name}")
                if (!exercise.hasReference) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Warning: Missing reference. Record one to get accuracy feedback.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                TextButton(
                    onClick = onConfirm,
                    enabled = exercise.hasReference
                ) {
                    Text(text = if (exercise.hasReference) "Start Exercise" else "Blocked (no ref)")
                }
                
                if (isCreationMode) {
                    TextButton(onClick = onRecordReference) {
                        Text(
                            text = if (exercise.hasReference) "Record new Reference" else "Record Reference now",
                            fontWeight = if (!exercise.hasReference) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun ActiveSessionOverlay(
    exerciseName: String,
    remainingTimeMs: Long?,
    elapsedTimeMs: Long?,
    isReadyCheck: Boolean = false,
    isCountdown: Boolean = false,
    countdownValue: Int = 0,
    isAnalyzing: Boolean = false,
    onStop: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Block clicks */ }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer(alpha = alpha),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = when {
                    isAnalyzing -> "Analyzing Strums..."
                    isCountdown -> "Prepare..."
                    isReadyCheck -> "Hold your arm steady..."
                    else -> "Session Active"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            if (isReadyCheck) {
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            if (isCountdown && countdownValue > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$countdownValue",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (!isCountdown && !isReadyCheck && !isAnalyzing && (remainingTimeMs != null || elapsedTimeMs != null)) {
                val time = remainingTimeMs ?: elapsedTimeMs ?: 0L
                val seconds = (time / 1000) % 60
                val minutes = (time / (1000 * 60)) % 60
                val timeText = String.format("%02d:%02d", minutes, seconds)
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (remainingTimeMs != null) "Time remaining: $timeText" else "Elapsed: $timeText",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("STOP SESSION")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Perform the strumming pattern on your guitar. The watch is capturing your movements.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
