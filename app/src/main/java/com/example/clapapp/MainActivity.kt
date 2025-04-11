package com.example.clapapp // Use your actual package name

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.clapapp.ui.theme.ClapAppTheme // Use your actual theme import
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

private const val TAG = "ClapApp"

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private lateinit var proximitySensorListener: SensorEventListener
    private var proximityMaxValue: Float = 5.0f // Default, will be updated
    private var previousValue: Float = -1.0f

    // Feedback
    private var soundPool: SoundPool? = null
    private var clapSoundId: Int = 0
    private var soundLoaded = false
    private var vibrator: Vibrator? = null

    // State for Compose UI
    private val statusText = mutableStateOf("Initializing...")
    private val proximityText = mutableStateOf("Proximity: -")
    private val imageRes = mutableStateOf(android.R.drawable.ic_media_pause) // Initial image


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d(TAG, "onCreate")

        // Initialize Sensor Manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (proximitySensor == null) {
            Log.e(TAG, "Proximity sensor not available.")
            statusText.value = "Proximity Sensor Not Found!"
            Toast.makeText(this, "Proximity sensor not found!", Toast.LENGTH_LONG).show()
        } else {
            proximityMaxValue = proximitySensor!!.maximumRange // !! is safe due to null check
            Log.i(TAG, "Proximity sensor found. Max Range: $proximityMaxValue")
            statusText.value = "Sensor Ready! Wave hand near top."
            setupSensorListener() // Setup listener logic only if sensor exists
        }

        // Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        }

        // Initialize SoundPool
        setupSoundPool()

        setContent {
            ClapAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ClapAppScreen(
                        status = statusText.value,
                        proximityValue = proximityText.value,
                        currentImageRes = imageRes.value,
                        modifier = Modifier.padding(innerPadding)
                    )
                    // Register sensor listener using lifecycle observer
                    SensorController(
                        sensorManager = sensorManager,
                        sensor = proximitySensor,
                        listener = proximitySensorListener
                    )
                }
            }
        }
    }

    private fun setupSensorListener() {
        proximitySensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
                    val currentValue = event.values[0]
                    // Log.v(TAG, "Sensor Value: $currentValue") // Verbose logging

                    proximityText.value = String.format("Proximity: %.1f cm", currentValue)

                    val closeThreshold = 0.0f // Usually 0.0 means very close
                    // Consider using a small range if needed: currentValue < 1.0f

                    val isClose = currentValue <= closeThreshold
                    val wasClose = previousValue <= closeThreshold // Check previous state

                    // Trigger ONLY on FAR -> CLOSE transition
                    if (isClose && !wasClose) {
                        Log.i(TAG, "CLAP DETECTED! (Value: $currentValue)")
                        triggerClapFeedback()
                    }
                    previousValue = currentValue // Update previous value *after* check
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "Proximity accuracy changed: $accuracy")
            }
        }
    }

    private fun setupSoundPool() {
        Log.d(TAG, "Setting up SoundPool")
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                soundLoaded = true
                Log.i(TAG, "Sound loaded successfully, ID: $sampleId")
            } else {
                soundLoaded = false
                Log.e(TAG, "Failed to load sound, status: $status")
                Toast.makeText(this@MainActivity, "Error loading sound", Toast.LENGTH_SHORT).show()
            }
        }

        // Load the sound - Replace R.raw.clap_sound if your filename is different
        clapSoundId = soundPool?.load(this, R.raw.clap_sound, 1) ?: 0
        Log.d(TAG, "Loading sound, requested ID: $clapSoundId")
    }


    private fun triggerClapFeedback() {
        Log.d(TAG, "Triggering clap feedback")
        // 1. Play Sound
        if (soundPool != null && soundLoaded && clapSoundId != 0) {
            Log.d(TAG,"Playing sound ID $clapSoundId")
            soundPool?.play(clapSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            Log.w(TAG,"SoundPool not ready or sound not loaded.")
        }

        // 2. Vibrate
        if (vibrator?.hasVibrator() == true) {
            Log.d(TAG,"Vibrating")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }

        // 3. Visual Feedback - Update State for Compose
        imageRes.value = android.R.drawable.ic_media_play // Change image state
        // Use coroutine launched from activity scope to reset image after delay
        lifecycleScope.launch {
            delay(200) // Delay for 200 milliseconds
            imageRes.value = android.R.drawable.ic_media_pause // Change image state back
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Releasing SoundPool")
        soundPool?.release()
        soundPool = null
    }

    // --- Inner Composable for Lifecycle Management ---
    @Composable
    private fun SensorController(
        sensorManager: SensorManager?,
        sensor: Sensor?,
        listener: SensorEventListener?
    ) {
        // If sensor or listener isn't setup (e.g., device lacks sensor), do nothing
        if (sensorManager == null || sensor == null || listener == null) {
            Log.w(TAG, "SensorController: Sensor, Manager, or Listener is null. Skipping registration.")
            return
        }

        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner, sensorManager, sensor, listener) {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    Log.d(TAG, "SensorController: Lifecycle onResume - Registering listener")
                    // Reset previous value when listener is registered
                    previousValue = -1.0f
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }

                override fun onPause(owner: LifecycleOwner) {
                    Log.d(TAG, "SensorController: Lifecycle onPause - Unregistering listener")
                    sensorManager.unregisterListener(listener)
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            // Cleanup function for DisposableEffect
            onDispose {
                Log.d(TAG, "SensorController: Disposing effect - Unregistering listener")
                sensorManager.unregisterListener(listener)
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
}


// --- Composable UI Function ---

@Composable
fun ClapAppScreen(
    status: String,
    proximityValue: String,
    currentImageRes: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = status,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = proximityValue,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Image(
                painter = painterResource(id = currentImageRes),
                contentDescription = "Clap Status Image",
                modifier = Modifier.size(100.dp)
            )
        }
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun ClapAppScreenPreview() {
    ClapAppTheme {
        ClapAppScreen(
            status = "Sensor Ready! Wave hand.",
            proximityValue = "Proximity: 5.0 cm",
            currentImageRes = android.R.drawable.ic_media_pause
        )
    }
}