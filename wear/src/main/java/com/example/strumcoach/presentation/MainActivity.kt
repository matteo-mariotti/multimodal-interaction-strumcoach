package com.example.strumcoach.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import com.example.strumcoach.presentation.screens.CountdownScreen
import com.example.strumcoach.presentation.screens.ExecutionScreen
import com.example.strumcoach.presentation.screens.HomeScreen
import com.example.strumcoach.presentation.screens.ReadyCheckScreen
import com.example.strumcoach.presentation.screens.SummaryScreen
import com.example.strumcoach.presentation.theme.StrumCoachTheme
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {
    private val viewModel: StrumCoachViewModel by viewModels()
    private lateinit var ambientObserver: AmbientLifecycleObserver
    private var isAmbientMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        ambientObserver = AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                isAmbientMode = true
            }
            override fun onExitAmbient() {
                isAmbientMode = false
            }
        })
        lifecycle.addObserver(ambientObserver)

        setContent {
            LaunchedEffect(viewModel.isRecordingActive, viewModel.currentScreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            WearApp(viewModel, isAmbientMode)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(ambientObserver)
    }

    override fun onStart() {
        super.onStart()
        Wearable.getCapabilityClient(this)
            .addLocalCapability("strum_coach_wear_active")
    }

    override fun onStop() {
        super.onStop()
        if (!viewModel.isRecordingActive) {
            Wearable.getCapabilityClient(this)
                .removeLocalCapability("strum_coach_wear_active")
        }
    }
}

@Composable
fun WearApp(viewModel: StrumCoachViewModel, isAmbientMode: Boolean) {
    val context = LocalContext.current
    val hapticHelper = HapticHelper(context)

    // Permessi delle notifiche
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        SideEffect {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.onTick = {
            hapticHelper.vibrateBeat()
        }
        viewModel.onPing = {
            hapticHelper.vibrateBeat()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hapticHelper.stop()
        }
    }

    // Vibra se ha terminato la sessione
    LaunchedEffect(viewModel.currentScreen) {
        if (viewModel.currentScreen == AppScreen.SUMMARY) {
            hapticHelper.vibrateSuccess()
        }
    }

    StrumCoachTheme {
        AppScaffold {
            when (viewModel.currentScreen) {
                AppScreen.HOME -> HomeScreen(
                    isConnected = viewModel.isPhoneConnected,
                    onOpenPhoneApp = viewModel::openPhoneApp
                )

                AppScreen.READY_CHECK -> ReadyCheckScreen(
                )

                AppScreen.COUNTDOWN -> CountdownScreen(
                    countdown = viewModel.countdownValue
                )

                AppScreen.EXECUTION -> ExecutionScreen(
                    exerciseName = viewModel.selectedExercise,
                    strummingPattern = viewModel.strummingPattern,
                    onStop = viewModel::stopExercise
                )

                AppScreen.SUMMARY -> SummaryScreen(
                    score = viewModel.score,
                    grade = viewModel.grade,
                    isWaitingForResult = viewModel.isWaitingForResult,
                    isReference = viewModel.isReferenceSession,
                    isAmbientMode = isAmbientMode,
                    onClose = viewModel::close
                )
            }
        }
    }
}
