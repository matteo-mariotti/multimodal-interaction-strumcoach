package com.example.strumcoach

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strumcoach.persistence.NetworkRepository
import com.example.strumcoach.persistence.StrumCoachApi
import com.example.strumcoach.persistence.StrumCoachRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.android.gms.wearable.CapabilityClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.util.Locale

data class Exercise(
    val id: String = "",
    val name: String,
    val difficulty: String,
    val color: androidx.compose.ui.graphics.Color,
    val authorName: String = "StrumCoach",
    val isSong: Boolean = false,
    val isPublic: Boolean = false,
    val referenceSignal: List<Float>? = null,
    val referenceStrums: List<StrumEvent>? = null,
    val hasReference: Boolean = false,
    val strummingPattern: String = "",
    val referenceAudioUrl: String? = null,
    val referenceDurationMs: Long? = null,
    val communitySourceId: String? = null,
    val userId: String? = null
)

data class SessionStats(
    val id: Long,
    val exerciseId: String = "",
    val exerciseName: String = "",
    val accuracy: Int,
    val grade: String,
    val isReference: Boolean = false,
    val timingData: List<Float>, // -1 to 1, where 0 is perfect
    val dynamicsFeedback: String,
    val rawSignal: List<Float> = emptyList(),
    val detectedStrums: List<StrumEvent> = emptyList(),
    val referenceSignal: List<Float>? = null,
    val referenceStrums: List<StrumEvent>? = null,
    val referenceAudioUrl: String? = null,
    val indexShift: Int = 0,
    val audioUrl: String? = null,
    val durationMs: Long = 0,
    val audioEnvelope: List<Float> = emptyList(),
    val gyroSignal: List<Float> = emptyList(),
    val debugInfo: Map<String, String> = emptyMap(),
    val audioOnsetThreshold: Float? = null,
    val userId: String? = null
)


class StrumCoachMobileViewModel(application: Application) : AndroidViewModel(application),
    CapabilityClient.OnCapabilityChangedListener,
    DataClient.OnDataChangedListener {

    private val auth = FirebaseAuth.getInstance()

    var currentUser by mutableStateOf(auth.currentUser)
        private set

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        currentUser = user
        if (user != null && !isCloudSyncActive) {
            if (creatorName.isEmpty()) {
                creatorName = user.displayName ?: ""
                prefs.edit().putString("creator_name", creatorName).apply()
            }
            initRepository()
        }
    }

    fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        viewModelScope.launch {
            try {
                auth.signInWithCredential(credential).await()
            } catch (e: Exception) {
                _errorEvents.emit("Sign-in failed: ${e.message}")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        isCloudSyncActive = false
        exercises = emptyList()
        songs = emptyList()
        communityExercises = emptyList()
        sessionHistory = emptyList()
    }

    private val capabilityClient = Wearable.getCapabilityClient(application)
    private val dataClient = Wearable.getDataClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    
    private val backendBaseUrl = "http://192.168.188.159:8000/" // Local Python FastAPI Backend (10.0.2.2 for Android Emulator)
    private val retrofit = Retrofit.Builder()
        .baseUrl(backendBaseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val api = retrofit.create(StrumCoachApi::class.java)
    private val repository: StrumCoachRepository = NetworkRepository(api, backendBaseUrl)
    
    private val audioManager = AudioManager(application)
    private val prefs = application.getSharedPreferences("strum_coach_data", Context.MODE_PRIVATE)

    var isWatchConnected by mutableStateOf(false)
        private set

    var watchScreen by mutableStateOf("UNKNOWN")
        private set

    var watchCountdown by mutableIntStateOf(0)
        private set

    var watchBatteryLevel by mutableIntStateOf(-1)
        private set

    var lastExerciseId by mutableStateOf(prefs.getString("last_exercise_id", "") ?: "")
        private set
        
    fun updateLastExerciseId(id: String) {
        lastExerciseId = id
        prefs.edit().putString("last_exercise_id", id).apply()
    }

    val weeklyMinutes by androidx.compose.runtime.derivedStateOf {
        val lastWeek = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val totalMs = sessionHistory
            .filter { it.id > lastWeek }
            .sumOf { it.durationMs }
        (totalMs / 60000).toInt()
    }

    val avgAccuracy by androidx.compose.runtime.derivedStateOf {
        val executions = sessionHistory.filter { !it.isReference }
        if (executions.isEmpty()) 0
        else executions.map { it.accuracy }.average().toInt()
    }

    var currentSessionReport by mutableStateOf<SessionStats?>(null)
        private set

    var songs by mutableStateOf<List<Exercise>>(emptyList())
        private set

    var exercises by mutableStateOf<List<Exercise>>(emptyList())
        private set

    var communityExercises by mutableStateOf<List<Exercise>>(emptyList())
        private set

    var sessionHistory by mutableStateOf<List<SessionStats>>(emptyList())
        private set

    var isRecordingReference by mutableStateOf(false)
        private set

    var currentlyPlayingUrl by mutableStateOf<String?>(null)
        private set

    var playbackProgress by mutableStateOf(0f)
        private set

    val playbackLabel by androidx.compose.runtime.derivedStateOf {
        val url = currentlyPlayingUrl ?: return@derivedStateOf ""
        currentSessionReport?.let { report ->
            if (url == report.audioUrl) return@derivedStateOf "${report.exerciseName} · Execution"
            if (url == report.referenceAudioUrl) return@derivedStateOf "${report.exerciseName} · Reference"
        }
        (exercises + songs + communityExercises).find { it.referenceAudioUrl == url }?.let { ex ->
            return@derivedStateOf "${ex.name} · Reference"
        }
        "Now Playing"
    }

    private var playbackPollingJob: kotlinx.coroutines.Job? = null

    var isCloudSyncActive by mutableStateOf(false)
        private set

    var sessionStartTime by mutableStateOf(0L)
        private set

    var sessionDurationMs by mutableStateOf(0L)
        private set

    var isAnalyzing by mutableStateOf(false)
        private set

    private var activeSessionJob: kotlinx.coroutines.Job? = null
    private var lastCapturedAudioFile: java.io.File? = null

    var creatorName by mutableStateOf(prefs.getString("creator_name", "") ?: "")
        private set

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    init {
        auth.addAuthStateListener(authListener)
        capabilityClient.addListener(this, "strum_coach_wear_active")
        dataClient.addListener(this)
        checkConnection()
        if (auth.currentUser != null) {
            initRepository()
        }
    }

    private fun initRepository() {
        isCloudSyncActive = true
        viewModelScope.launch {
            try {
                // Observe user private exercises
                launch {
                    repository.observeExercises().collectLatest { cloudExercises ->
                        exercises = cloudExercises.filter { !it.isSong }
                        songs = cloudExercises.filter { it.isSong }
                    }
                }
                // Observe community exercises
                launch {
                    repository.observeCommunityExercises().collectLatest { shared ->
                        communityExercises = shared
                    }
                }
                // Observe session history
                launch {
                    repository.observeSessions().collectLatest { history ->
                        sessionHistory = history
                    }
                }
            } catch (e: Exception) {
                isCloudSyncActive = false
                _errorEvents.emit("Failed to connect to backend.")
            }
        }
    }

    var isRefreshing by mutableStateOf(false)
        private set

    fun refreshData() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                val uid = repository.getUserId() ?: ""
                val exResponse = api.getExercises(userId = uid)
                if (exResponse.isSuccessful) {
                    val cloudExercises = exResponse.body() ?: emptyList()
                    exercises = cloudExercises.filter { !it.isSong }
                    songs = cloudExercises.filter { it.isSong }
                }
                val commResponse = api.getCommunityExercises()
                if (commResponse.isSuccessful) {
                    communityExercises = commResponse.body() ?: emptyList()
                }
                val sessResponse = api.getSessions(userId = uid)
                if (sessResponse.isSuccessful) {
                    sessionHistory = sessResponse.body() ?: emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("ViewModel", "Error refreshing data", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            try {
                val capabilityInfo = capabilityClient
                    .getCapability("strum_coach_wear_active", CapabilityClient.FILTER_REACHABLE)
                    .await()
                isWatchConnected = capabilityInfo.nodes.isNotEmpty()
            } catch (e: Exception) {
                isWatchConnected = false
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: com.google.android.gms.wearable.CapabilityInfo) {
        isWatchConnected = capabilityInfo.nodes.isNotEmpty()
    }

    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        for (event in dataEvents) {
            when (event.dataItem.uri.path) {
                "/watch_state" -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val newScreen = dataMap.getString("screen", "UNKNOWN")
                    watchCountdown = dataMap.getInt("countdown", 0)
                    android.util.Log.d("ViewModel", "Watch state changed: $watchScreen -> $newScreen")
                    
                    if (newScreen == "EXECUTION" && watchScreen != "EXECUTION") {
                        lastCapturedAudioFile = null
                        sessionStartTime = System.currentTimeMillis()
                        val fileName = if (isRecordingReference) {
                            "reference_${System.currentTimeMillis()}.m4a"
                        } else {
                            "session_latest.m4a"
                        }
                        val file = audioManager.startRecording(fileName, persistent = isRecordingReference)
                        if (file != null) {
                            android.util.Log.d("ViewModel", "Recording started: ${file.name}")
                        } else {
                            android.util.Log.e("ViewModel", "Failed to start recording")
                        }
                    } else if (newScreen != "EXECUTION" && newScreen != "COUNTDOWN" && newScreen != "READY_CHECK" && (watchScreen == "EXECUTION" || watchScreen == "COUNTDOWN")) {
                        android.util.Log.d("ViewModel", "Execution stopped on watch, stopping audio capture")
                        isAnalyzing = true
                        activeSessionJob?.cancel()
                        activeSessionJob = null
                        sessionDurationMs = 0
                        sessionStartTime = 0
                        lastCapturedAudioFile = audioManager.stopRecording()
                        if (lastCapturedAudioFile != null) {
                            android.util.Log.d("ViewModel", "Audio captured: ${lastCapturedAudioFile?.length()} bytes")
                        }
                    }
                    
                    watchScreen = newScreen
                }
                "/battery" -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    watchBatteryLevel = dataMap.getInt("level", -1)
                }
                "/sensor_data" -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val asset = dataMap.getAsset("data")
                    if (asset != null) {
                        processSensorAsset(asset)
                    }
                }
            }
        }
    }

    private fun processSensorAsset(asset: com.google.android.gms.wearable.Asset) {
        viewModelScope.launch {
            try {
                val inputStream = dataClient.getFdForAsset(asset).await().inputStream
                val bytes = inputStream.readBytes()
                analyzeSensorData(bytes)
            } catch (e: Exception) {
                isAnalyzing = false
            }
        }
    }

    fun findExercise(id: String): Exercise? {
        return exercises.find { it.id == id } ?: songs.find { it.id == id } ?: communityExercises.find { it.id == id }
    }

    private fun analyzeSensorData(bytes: ByteArray) {
        val audioFile = lastCapturedAudioFile ?: audioManager.stopRecording()
        lastCapturedAudioFile = null
        
        val audioEnvelope = audioFile?.let { AudioEnvelopeExtractor.extractEnvelope(it) }
        val spectralFlux = audioFile?.let { SpectralFluxExtractor.extractFlux(it) }

        val buffer = ByteBuffer.wrap(bytes)
        val dataCount = bytes.size / (5 * 4)

        // Only TYPE_GYROSCOPE (4f) is captured on the watch (see RecordingService) —
        // the accelerometer was dropped once detection stopped using it for anything
        // beyond supplying a shared clock, a role gyroTS already fills on its own.
        val gyroX = mutableListOf<Float>()
        val gyroY = mutableListOf<Float>()
        val gyroZ = mutableListOf<Float>()
        val gyroTS = mutableListOf<Float>()

        for (i in 0 until dataCount) {
            val type = buffer.float
            val ts = buffer.float
            val x = buffer.float
            val y = buffer.float
            val z = buffer.float

            if (type == 4f) { // TYPE_GYROSCOPE
                gyroX.add(x)
                gyroY.add(y)
                gyroZ.add(z)
                gyroTS.add(ts)
            }
        }

        if (gyroTS.isEmpty()) {
            isAnalyzing = false
            return
        }

        val exercise = findExercise(lastExerciseId)
        val duration = (gyroTS.last() - gyroTS.first()).toLong()
        val isReferenceAtStart = isRecordingReference

        val result = StrumAnalyzer.analyze(
            gyroZ,
            gyroTS,
            exercise?.referenceSignal,
            exercise?.referenceStrums,
            audioEnvelope,
            spectralFlux
        )

        // Export debug CSV
        exportDebugCsv(
            gyroTS, gyroX, gyroY, gyroZ,
            result
        )
        
        // Export detected strums
        exportStrumsCsv("strum_test_detected.csv", result.detectedStrums)
        
        // Export reference strums if available
        exercise?.referenceStrums?.let {
            exportStrumsCsv("strum_test_reference.csv", it)
        }

        if (isReferenceAtStart && exercise != null) {
            val audioPath = audioFile?.absolutePath
            saveReferenceInMemory(exercise, result, audioPath, duration)
            isRecordingReference = false
        }

        currentSessionReport = SessionStats(
            id = System.currentTimeMillis(),
            exerciseId = exercise?.id ?: "",
            exerciseName = exercise?.name ?: "Exercise",
            accuracy = result.accuracy,
            grade = when {
                result.accuracy >= 90 -> "Excellent!"
                result.accuracy >= 75 -> "Great!"
                result.accuracy >= 50 -> "Good!"
                else -> "Keep practicing!"
            },
            isReference = isReferenceAtStart,
            timingData = result.timingOffsets,
            dynamicsFeedback = result.feedback,
            rawSignal = result.processedSignal,
            detectedStrums = result.detectedStrums,
            referenceSignal = exercise?.referenceSignal,
            referenceStrums = exercise?.referenceStrums,
            referenceAudioUrl = exercise?.referenceAudioUrl,
            indexShift = result.indexShift,
            audioUrl = audioFile?.absolutePath,
            durationMs = duration,
            audioEnvelope = result.audioEnvelope,
            gyroSignal = result.gyroSignal,
            debugInfo = result.debugData,
            audioOnsetThreshold = result.audioOnsetThreshold
        )

        // Save session
        viewModelScope.launch {
            val success = currentSessionReport?.let { repository.saveSession(it) } ?: true
            if (!success) {
                _errorEvents.emit("Error saving session results.")
            }
        }

        // Send result to watch for synchronization
        viewModelScope.launch {
            try {
                val nodes = capabilityClient
                    .getCapability("strum_coach_wear_active", CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                val resultData = "${result.accuracy}|${currentSessionReport?.grade}|${if (isReferenceAtStart) 1 else 0}".toByteArray()
                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/session_result", resultData).await()
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isAnalyzing = false
            }
        }
    }

    private fun saveReferenceInMemory(exercise: Exercise, result: AnalysisResult, audioUrl: String? = null, durationMs: Long? = null) {
        exercises = exercises.map {
            if (it.id == exercise.id) {
                it.copy(
                    referenceSignal = result.processedSignal,
                    referenceStrums = result.detectedStrums,
                    hasReference = true,
                    referenceAudioUrl = audioUrl ?: it.referenceAudioUrl,
                    referenceDurationMs = durationMs ?: it.referenceDurationMs
                )
            } else it
        }
        
        viewModelScope.launch {
            val success = repository.saveReference(exercise.id, result.processedSignal, result.detectedStrums, audioUrl, durationMs)
            if (!success) {
                _errorEvents.emit("Error saving exercise reference.")
            }
        }
    }

    fun iniziaEsercizio(exerciseId: String) {
        val exercise = findExercise(exerciseId)
        updateLastExerciseId(exerciseId)
        activeSessionJob?.cancel()
        
        viewModelScope.launch {
            try {
                val nodes = capabilityClient
                    .getCapability("strum_coach_wear_active", CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                
                val name = exercise?.name ?: "Exercise"
                val pattern = exercise?.strummingPattern ?: ""
                val duration = exercise?.referenceDurationMs ?: 0L
                val payload = "$exerciseId|$name|$pattern|$duration".toByteArray()

                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/start_exercise", payload).await()
                }

                sessionDurationMs = duration
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun ping() {
        viewModelScope.launch {
            try {
                val nodes = capabilityClient
                    .getCapability("strum_coach_wear_active", CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/ping", null).await()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun terminaEsercizio() {
        viewModelScope.launch {
            try {
                val nodes = capabilityClient
                    .getCapability("strum_coach_wear_active", CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/stop", null).await()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun openWatchApp() {
        viewModelScope.launch {
            try {
                val activeNodes = capabilityClient
                    .getCapability("strum_coach_wear_active", CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                
                val targetNodes = activeNodes.ifEmpty {
                    Wearable.getNodeClient(getApplication<Application>()).connectedNodes.await()
                }

                for (node in targetNodes) {
                    messageClient.sendMessage(node.id, "/open_app", null).await()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun startReferenceRecording(exerciseId: String): Boolean {
        if (!isWatchConnected) {
            viewModelScope.launch {
                _errorEvents.emit("Smartwatch not connected. Please open the StrumCoach app on your watch.")
            }
            openWatchApp()
            return false
        }
        lastExerciseId = exerciseId
        updateLastExerciseId(exerciseId)
        isRecordingReference = true
        sessionDurationMs = 0
        iniziaEsercizio(exerciseId)
        return true
    }

    fun addNewExercise(
        name: String, 
        difficulty: String, 
        color: androidx.compose.ui.graphics.Color, 
        isSong: Boolean, 
        pattern: String = "",
        onCreated: (Exercise) -> Unit = {}
    ) {
        val newId = "custom_${System.currentTimeMillis()}" // Evita conflitti sugli id
        val newExercise = Exercise(
            id = newId, 
            name = name, 
            difficulty = difficulty, 
            color = color, 
            authorName = "Me", 
            isSong = isSong, 
            strummingPattern = pattern
        )
        viewModelScope.launch {
            val success = repository.saveExercise(newExercise)
            if (success) {
                refreshData()
                onCreated(newExercise)
            } else {
                _errorEvents.emit("Error saving exercise. Check your connection.")
            }
        }
    }

    fun updateExercise(
        original: Exercise,
        name: String,
        difficulty: String,
        color: androidx.compose.ui.graphics.Color,
        isSong: Boolean,
        pattern: String = ""
    ) {
        val updated = original.copy(
            name = name,
            difficulty = difficulty,
            color = color,
            isSong = isSong,
            strummingPattern = pattern
        )
        viewModelScope.launch {
            val privateOk = repository.saveExercise(updated)
            val communityOk = if (original.isPublic) repository.saveExercise(updated, isCommunity = true) else true
            if (privateOk && communityOk) {
                refreshData()
            } else {
                _errorEvents.emit("Error updating exercise. Check your connection.")
            }
        }
    }

    fun pubblica(exercise: Exercise, onResult: (Boolean) -> Unit) {
        val nameToUse = creatorName.ifEmpty { "Anonymous" }
        viewModelScope.launch {
            val updated = exercise.copy(authorName = nameToUse, isPublic = true)
            val success = repository.saveExercise(updated, isCommunity = true)
            if (success) {
                repository.saveExercise(updated)
                refreshData() // Aggiorna lo stato sull'app
            } else {
                _errorEvents.emit("Error publishing to community.")
            }
            onResult(success)
        }
    }

    fun eliminaEsercizio(exercise: Exercise) {
        viewModelScope.launch {
            val success = repository.deleteExercise(exercise.id)
            if (success) {
                refreshData()
            } else {
                _errorEvents.emit("Error deleting exercise.")
            }
        }
    }

    fun deleteFromCommunity(exercise: Exercise) {
        viewModelScope.launch {
            val success = repository.deleteFromCommunity(exercise.id)
            if (success) {
                val localEx = (exercises + songs).find { it.id == exercise.id || it.communitySourceId == exercise.id }
                if (localEx != null) {
                    repository.saveExercise(localEx.copy(isPublic = false))
                }
                refreshData()
            } else {
                _errorEvents.emit("Error removing from community.")
            }
        }
    }

    fun downloadFromCommunity(exercise: Exercise, onResult: (Boolean, String) -> Unit) {
        val exists = (exercises + songs).any { it.communitySourceId == exercise.id || it.id == exercise.id }
        if (exists) {
            onResult(false, "Already in your library")
            return
        }

        viewModelScope.launch {
            val success = repository.saveExercise(exercise.copy(id = "", communitySourceId = exercise.id))
            if (success) {
                refreshData()
                onResult(true, "Added to library!")
            } else {
                _errorEvents.emit("Error downloading exercise.")
                onResult(false, "Error during download")
            }
        }
    }


    fun showSessionReport(stats: SessionStats) {
        currentSessionReport = stats
    }

    fun clearReport() {
        currentSessionReport = null
    }

    fun togglePlayback(url: String) {
        if (currentlyPlayingUrl == url) {
            stopPlayback()
        } else {
            currentlyPlayingUrl = url
            playbackProgress = 0f
            audioManager.playAudio(url, onComplete = {
                currentlyPlayingUrl = null
                playbackProgress = 0f
                playbackPollingJob?.cancel()
                playbackPollingJob = null
            })
            playbackPollingJob?.cancel()
            playbackPollingJob = viewModelScope.launch {
                while (currentlyPlayingUrl == url) {
                    val duration = audioManager.getDurationMs()
                    val position = audioManager.getCurrentPositionMs()
                    playbackProgress = if (duration > 0) position.toFloat() / duration else 0f
                    kotlinx.coroutines.delay(200)
                }
            }
        }
    }

    fun stopPlayback() {
        currentlyPlayingUrl = null
        playbackProgress = 0f
        playbackPollingJob?.cancel()
        playbackPollingJob = null
        audioManager.stopPlayback()
    }

    private fun exportDebugCsv(
        timestamps: List<Float>,
        gyroX: List<Float>,
        gyroY: List<Float>,
        gyroZ: List<Float>,
        result: AnalysisResult
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(getApplication<Application>().getExternalFilesDir(null), "strum_test.csv")
                java.io.FileWriter(file).use { writer ->
                    writer.write("timestamp,gyroX,gyroY,gyroZ,rawZ,smoothed,audioEnvelope\n")

                    val startTime = timestamps.firstOrNull() ?: 0f

                    // We need to map the full timestamps back to the trimmed indices used in AnalysisResult
                    // Result signals (rawZ, smoothed) are already trimmed and have the same size.
                    val trimmedSize = result.rawZ.size
                    if (trimmedSize == 0) return@launch

                    // For simplicity, let's just export the trimmed range where we have all intermediate steps
                    val startIndex = timestamps.indexOfFirst { it >= startTime + StrumAnalyzer.TRIM_START_MS }

                    for (i in 0 until trimmedSize) {
                        val fullIdx = startIndex + i
                        if (fullIdx < 0 || fullIdx >= timestamps.size) continue

                        val ts = timestamps[fullIdx]
                        val gx = gyroX[fullIdx]
                        val gy = gyroY[fullIdx]
                        val gz = gyroZ[fullIdx]

                        val rz = result.rawZ[i]
                        val sm = result.processedSignal[i]
                        val env = result.audioEnvelope.getOrElse(i) { 0f }

                        writer.write(String.format(Locale.US, "%.0f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f\n",
                            ts, gx, gy, gz, rz, sm, env))
                    }
                }
                android.util.Log.d("ViewModel", "Debug CSV exported to: ${file.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("ViewModel", "Error exporting CSV", e)
            }
        }
    }

    private fun exportStrumsCsv(fileName: String, strums: List<StrumEvent>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(getApplication<Application>().getExternalFilesDir(null), fileName)
                java.io.FileWriter(file).use { writer ->
                    writer.write("index,timestamp,value,isDown\n")
                    strums.forEach { strum ->
                        writer.write(String.format(Locale.US, "%d,%.0f,%.4f,%b\n",
                            strum.index, strum.timestamp, strum.value, strum.isDown))
                    }
                }
                android.util.Log.d("ViewModel", "Strums CSV exported to: ${file.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("ViewModel", "Error exporting strums CSV", e)
            }
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        capabilityClient.removeListener(this)
        dataClient.removeListener(this)
        super.onCleared()
    }
}
