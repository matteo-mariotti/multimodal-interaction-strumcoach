package com.example.strumcoach.presentation

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class RecordingService : Service(), SensorEventListener {

    private val serviceJob = SupervisorJob()

    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startTimeMillis: Long = 0

    private val sensorBuffer = mutableListOf<FloatArray>()

    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StrumCoach:RecordingWakeLock")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationBuilder = createNotificationBuilder()
        
        val status = Status.Builder()
            .addTemplate("StrumCoach: Session active")
            .build()

        val ongoingActivity = OngoingActivity.Builder(this, NOTIFICATION_ID, notificationBuilder)
            .setStaticIcon(android.R.drawable.ic_media_play)
            .setTouchIntent(
                android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
            )
            .setStatus(status)
            .build()

        ongoingActivity.apply(this)
        
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        wakeLock?.acquire(5 * 60 * 1000L /* 5 min */)
        
        startTimeMillis = System.currentTimeMillis()
        sensorBuffer.clear()
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)

        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val data = floatArrayOf(
            event.sensor.type.toFloat(),
            (System.currentTimeMillis() - startTimeMillis).toFloat(),
            event.values[0],
            event.values[1],
            event.values[2]
        )
        synchronized(sensorBuffer) {
            sensorBuffer.add(data)
            if (sensorBuffer.size > 10000) sensorBuffer.removeAt(0)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        
        runBlocking {
            withContext(NonCancellable) {
                sendDataToPhone()
            }
        }
        
        serviceJob.cancel()
        super.onDestroy()
    }

    @SuppressLint("VisibleForTests")
    private suspend fun sendDataToPhone() {
        val dataCopy = synchronized(sensorBuffer) { sensorBuffer.toList() }
        if (dataCopy.isEmpty()) return

        val byteBuffer = ByteBuffer.allocate(dataCopy.size * 5 * 4)
        for (row in dataCopy) {
            for (value in row) {
                byteBuffer.putFloat(value)
            }
        }

        val asset = Asset.createFromBytes(byteBuffer.array())
        val dataClient = Wearable.getDataClient(this)
        
        try {
            val putDataMapReq = PutDataMapRequest.create("/sensor_data").apply {
                dataMap.putAsset("data", asset)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            dataClient.putDataItem(putDataMapReq.asPutDataRequest().setUrgent()).await()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StrumCoach")
            .setContentText("Strum recording in progress...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Recording Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
