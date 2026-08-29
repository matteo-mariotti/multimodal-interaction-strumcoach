package com.example.strumcoach.presentation

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.time.Duration.Companion.milliseconds

enum class AppScreen {
    HOME,
    READY_CHECK,
    COUNTDOWN,
    EXECUTION,
    SUMMARY
}

class StrumCoachViewModel(application: Application) : AndroidViewModel(application),
    CapabilityClient.OnCapabilityChangedListener,
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {

    private val capabilityClient = Wearable.getCapabilityClient(application)
    private val dataClient = Wearable.getDataClient(application)
    private val messageClient = Wearable.getMessageClient(application)

    var currentScreen by mutableStateOf(AppScreen.HOME)
        private set

    var isPhoneConnected by mutableStateOf(false)
        private set

    var selectedExercise by mutableStateOf("Waiting...")
        private set

    var strummingPattern by mutableStateOf("")
        private set

    var score by mutableIntStateOf(0)
        private set

    var grade by mutableStateOf("")
        private set

    var isReferenceSession by mutableStateOf(false)
        private set

    var isRecordingActive by mutableStateOf(false)
        private set

    var countdownValue by mutableIntStateOf(5)
        private set

    var isWaitingForResult by mutableStateOf(false)
        private set

    private var exerciseDurationMs: Long = 0L
    private var countdownJob: Job? = null
    private var autoStopJob: Job? = null
    private var readyCheckJob: Job? = null
    private var readyStartTime: Long = 0L
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    var onTick: (() -> Unit)? = null
    var onPing: (() -> Unit)? = null

    private val sensorEventListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            if (currentScreen != AppScreen.READY_CHECK) return
            
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Check for stillness: compare with last known values
            // A change < 0.5 m/s^2 on all axes is considered "still"
            val deltaX = kotlin.math.abs(x - lastX)
            val deltaY = kotlin.math.abs(y - lastY)
            val deltaZ = kotlin.math.abs(z - lastZ)
            
            val isStill = deltaX < 0.5f && deltaY < 0.5f && deltaZ < 0.5f
            
            if (isStill) {
                if (readyStartTime == 0L) {
                    readyStartTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - readyStartTime > 1500) {
                    confirmReady()
                }
            } else {
                // Moving: reset the timer and update reference values
                readyStartTime = 0L
                lastX = x
                lastY = y
                lastZ = z
            }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    private fun confirmReady() {
        if (readyCheckJob != null) return
        readyCheckJob = viewModelScope.launch {
            onPing?.invoke() // Short vibration feedback
            delay(500.milliseconds)
            readyStartTime = 0L
            actuallyStartCountdown()
        }
    }

    init {
        capabilityClient.addListener(this, "strum_coach_mobile_active")
        dataClient.addListener(this)
        messageClient.addListener(this)
        checkConnection()
        syncWatchState()
        startBatterySync()
    }

    private fun startBatterySync() {
        viewModelScope.launch {
            while (isActive) {
                syncBattery()
                delay(300000.milliseconds) // Sync every 5 minutes
            }
        }
    }

    @SuppressLint("VisibleForTests")
    private fun syncBattery() {
        val batteryStatus: Intent? = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            getApplication<Application>().registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = (level * 100 / scale.toFloat()).toInt()

        viewModelScope.launch {
            try {
                val putDataMapReq = PutDataMapRequest.create("/battery").apply {
                    dataMap.putInt("level", batteryPct)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }
                dataClient.putDataItem(putDataMapReq.asPutDataRequest().setUrgent())
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun checkConnection() {
        viewModelScope.launch {
            try {
                val capabilityInfo = capabilityClient
                    .getCapability("strum_coach_mobile_active", CapabilityClient.FILTER_REACHABLE)
                    .await()
                isPhoneConnected = capabilityInfo.nodes.isNotEmpty()
            } catch (e: Exception) {
                isPhoneConnected = false
            }
        }
    }

    @SuppressLint("VisibleForTests")
    private fun syncWatchState() {
        viewModelScope.launch {
            try {
                val putDataMapReq = PutDataMapRequest.create("/watch_state").apply {
                    dataMap.putString("screen", currentScreen.name)
                    dataMap.putInt("countdown", countdownValue)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }
                dataClient.putDataItem(putDataMapReq.asPutDataRequest().setUrgent()).await()
            } catch (e: Exception) {
                // Ignore sync errors
            }
        }
    }

    private fun updateScreen(newScreen: AppScreen) {
        currentScreen = newScreen
        syncWatchState()
    }

    override fun onCapabilityChanged(capabilityInfo: com.google.android.gms.wearable.CapabilityInfo) {
        isPhoneConnected = capabilityInfo.nodes.isNotEmpty()
        if (!isPhoneConnected && currentScreen != AppScreen.HOME) {
            updateScreen(AppScreen.HOME)
        }
    }

    @SuppressLint("VisibleForTests")
    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/exercise") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                selectedExercise = dataMap.getString("name", "Exercise")
                strummingPattern = dataMap.getString("pattern", "")
                if (currentScreen == AppScreen.HOME) {
                    startExercise()
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/ping" -> onPing?.invoke()
            "/stop" -> {
                if (currentScreen == AppScreen.EXECUTION) {
                    stopExercise()
                }
            }
            "/start_exercise" -> {
                val payload = String(messageEvent.data)
                if (payload.contains("|")) {
                    val parts = payload.split("|")
                    selectedExercise = parts[1]
                    strummingPattern = if (parts.size > 2) parts[2] else ""
                    exerciseDurationMs = if (parts.size > 3) parts[3].toLongOrNull() ?: 0L else 0L
                } else {
                    selectedExercise = payload
                    strummingPattern = ""
                    exerciseDurationMs = 0L
                }
                startExercise()
            }
            "/session_result" -> {
                val data = String(messageEvent.data).split("|")
                if (data.size >= 2) {
                    score = data[0].toIntOrNull() ?: 0
                    grade = data[1]
                    if (data.size >= 3) {
                        isReferenceSession = data[2] == "1"
                    }
                    isWaitingForResult = false
                }
            }
        }
    }

    fun startExercise() {
        isReferenceSession = false
        countdownJob?.cancel()
        readyCheckJob?.cancel()
        readyCheckJob = null
        
        // Start monitoring accelerometer for ready position
        val sensorManager = getApplication<Application>().getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val accel = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(sensorEventListener, accel, android.hardware.SensorManager.SENSOR_DELAY_UI)
        
        updateScreen(AppScreen.READY_CHECK)
    }

    private fun actuallyStartCountdown() {
        val sensorManager = getApplication<Application>().getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        sensorManager.unregisterListener(sensorEventListener)
        
        countdownJob = viewModelScope.launch {
            countdownValue = 5
            updateScreen(AppScreen.COUNTDOWN)
            while (countdownValue > 0) {
                onTick?.invoke()
                delay(1000.milliseconds)
                countdownValue--
                syncWatchState()
            }
            actuallyStartExercise()
        }
    }

    private fun actuallyStartExercise() {
        updateScreen(AppScreen.EXECUTION)
        isRecordingActive = true
        startService()
        
        // Start auto-stop timer if duration is set
        autoStopJob?.cancel()
        if (exerciseDurationMs > 0) {
            autoStopJob = viewModelScope.launch {
                delay(exerciseDurationMs.milliseconds)
                if (currentScreen == AppScreen.EXECUTION) {
                    stopExercise()
                }
            }
        }
    }

    fun stopExercise() {
        countdownJob?.cancel()
        autoStopJob?.cancel()
        isRecordingActive = false
        stopService()
        isWaitingForResult = true
        updateScreen(AppScreen.SUMMARY)
    }

    private fun startService() {
        val intent = Intent(getApplication(), RecordingService::class.java)
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopService() {
        val intent = Intent(getApplication(), RecordingService::class.java)
        getApplication<Application>().stopService(intent)
    }

    fun close() {
        val sensorManager = getApplication<Application>().getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        sensorManager.unregisterListener(sensorEventListener)
        readyCheckJob?.cancel()
        readyCheckJob = null
        updateScreen(AppScreen.HOME)
    }

    fun openPhoneApp() {
        viewModelScope.launch {
            try {
                val nodes = capabilityClient
                    .getCapability("strum_coach_mobile_active", CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                // If not active, try all nodes to wake up the phone
                val targetNodes = nodes.ifEmpty {
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

    override fun onCleared() {
        super.onCleared()
        val sensorManager = getApplication<Application>().getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        sensorManager.unregisterListener(sensorEventListener)
        capabilityClient.removeListener(this)
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        countdownJob?.cancel()
        autoStopJob?.cancel()
        readyCheckJob?.cancel()
    }
}
