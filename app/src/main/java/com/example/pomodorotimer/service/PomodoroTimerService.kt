package com.example.pomodorotimer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pomodorotimer.R
import com.example.pomodorotimer.data.PomodoroDatabase
import com.example.pomodorotimer.data.entity.PomodoroSession
import com.example.pomodorotimer.data.repository.PomodoroRepository
import com.example.pomodorotimer.ui.MainActivity
import com.example.pomodorotimer.util.Constants
import com.example.pomodorotimer.util.Constants.ACCELEROMETER_SENSITIVITY
import com.example.pomodorotimer.util.Constants.NOTIFICATION_CHANNEL_ID
import com.example.pomodorotimer.util.Constants.NOTIFICATION_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.sqrt

class PomodoroTimerService : Service(), SensorEventListener {

    private val binder = LocalBinder()
    private var isFirstRun = true
    private var isTimerRunning = false
    private var isPaused = false
    private var sessionFailed = false
    private var timerCompleted = false

    private var timerDurationMillis = 0L
    private var timeLeftMillis = 0L
    private var endTime = 0L
    private var timerStartTime = 0L
    private val ACCELEROMETER_GRACE_PERIOD = 1500L // 3 seconds grace period

    private lateinit var timer: CountDownTimer
    private lateinit var repository: PomodoroRepository

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val _timeLeftLiveData = MutableLiveData<Long>()
    val timeLeftLiveData: LiveData<Long> = _timeLeftLiveData

    private val _isRunningLiveData = MutableLiveData<Boolean>()
    val isRunningLiveData: LiveData<Boolean> = _isRunningLiveData

    private val _isPausedLiveData = MutableLiveData<Boolean>()
    val isPausedLiveData: LiveData<Boolean> = _isPausedLiveData

    private val _sessionFailedLiveData = MutableLiveData<Boolean>()
    val sessionFailedLiveData: LiveData<Boolean> = _sessionFailedLiveData

    private val _timerCompletedLiveData = MutableLiveData<Boolean>()
    val timerCompletedLiveData: LiveData<Boolean> = _timerCompletedLiveData

    inner class LocalBinder : Binder() {
        fun getService(): PomodoroTimerService = this@PomodoroTimerService
    }

    override fun onCreate() {
        super.onCreate()

        val dao = PomodoroDatabase.getDatabase(this).pomodoroSessionDao()
        repository = PomodoroRepository(dao)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        createNotificationChannel() // Ensure the notification channel is created
    }

    private fun createNotificationChannel() {
        val channelId = NOTIFICATION_CHANNEL_ID
        val channelName = "Pomodoro Timer"
        val channelDescription = "Notifications for Pomodoro Timer Service"

        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Call startForeground() immediately to prevent timeout
        startForeground(NOTIFICATION_ID, createNotification("Starting timer..."))

        intent?.let {
            when (it.action) {
                Constants.ACTION_START_OR_RESUME_SERVICE -> {
                    // Reset timer completed flag when starting a new timer
                    timerCompleted = false
                    _timerCompletedLiveData.postValue(false)

                    // Always get the duration from the intent if provided
                    val intentDuration = it.getLongExtra(Constants.EXTRA_TIMER_DURATION, -1L)
                    if (intentDuration > 0) {
                        timerDurationMillis = intentDuration
                        timeLeftMillis = timerDurationMillis
                        Log.d("PomodoroTimer", "Using duration from intent: ${timerDurationMillis/1000/60} minutes")
                    } else if (isFirstRun) {
                        // Fallback to default only if no duration provided and first run
                        timerDurationMillis = 25 * 60 * 1000L
                        timeLeftMillis = timerDurationMillis
                        Log.d("PomodoroTimer", "Using default duration: 25 minutes")
                    }

                    _timeLeftLiveData.postValue(timeLeftMillis)

                    if (isPaused) {
                        startTimer()
                        isPaused = false
                        _isPausedLiveData.postValue(false)
                    } else {
                        isFirstRun = false
                        startForegroundService()
                    }
                }
                Constants.ACTION_PAUSE_SERVICE -> {
                    pauseTimer()
                }
                Constants.ACTION_STOP_SERVICE -> {
                    stopTimer()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun startForegroundService() {
        // Call startForeground() immediately
        startForeground(NOTIFICATION_ID, createNotification("Timer is running..."))

        isTimerRunning = true
        _isRunningLiveData.postValue(true)
        sessionFailed = false
        _sessionFailedLiveData.postValue(false)

        // Make sure we have a valid duration
        if (timerDurationMillis <= 0) {
            timerDurationMillis = 25 * 60 * 1000L // Default to 25 minutes if something went wrong
            Log.d("PomodoroTimer", "Timer duration was invalid, defaulting to 25 minutes")
        }

        // Set the time left to the full duration
        timeLeftMillis = timerDurationMillis
        _timeLeftLiveData.postValue(timeLeftMillis)
        Log.d("PomodoroTimer", "Starting service with ${timeLeftMillis}ms remaining")

        // Start the timer with the full duration
        startTimer()

        // Register accelerometer with a delay to give user time to set phone down
        Handler(Looper.getMainLooper()).postDelayed({
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("PomodoroTimer", "Accelerometer registered after delay")
            }
        }, ACCELEROMETER_GRACE_PERIOD) // 3 second delay
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pomodoro Timer")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startTimer() {
        // Add additional safety check
        if (timeLeftMillis < 1000) { // If less than 1 second, reset
            timeLeftMillis = timerDurationMillis
            Log.d("PomodoroTimer", "Time left was too small, reset to ${timeLeftMillis}ms")
        }

        endTime = System.currentTimeMillis() + timeLeftMillis
        timerStartTime = System.currentTimeMillis() // Record when timer started for grace period

        // Log the starting time for debugging
        Log.d("PomodoroTimer", "Starting timer with ${timeLeftMillis}ms remaining")

        timer = object : CountDownTimer(timeLeftMillis, Constants.TIMER_UPDATE_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMillis = millisUntilFinished
                _timeLeftLiveData.postValue(timeLeftMillis)
                updateNotification()

                // Log for debugging
                Log.d("PomodoroTimer", "Tick: ${timeLeftMillis}ms remaining")
            }

            override fun onFinish() {
                // Log for debugging
                Log.d("PomodoroTimer", "Timer finished")

                // Set timer completed flag
                timerCompleted = true
                _timerCompletedLiveData.postValue(true)

                // Reset timeLeftMillis to original duration for display purposes
                timeLeftMillis = timerDurationMillis
                _timeLeftLiveData.postValue(timeLeftMillis)

                isTimerRunning = false
                _isRunningLiveData.postValue(false)

                if (!sessionFailed) {
                    saveSession(true)
                    showCompletionNotification()
                }

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()

        isTimerRunning = true
        _isRunningLiveData.postValue(true)
    }

    private fun pauseTimer() {
        if (!isTimerRunning) return

        timer.cancel()
        isPaused = true
        _isPausedLiveData.postValue(true)
        isTimerRunning = false
        _isRunningLiveData.postValue(false)
        timeLeftMillis = endTime - System.currentTimeMillis()

        // Ensure we don't have negative time
        if (timeLeftMillis < 0) timeLeftMillis = 0

        _timeLeftLiveData.postValue(timeLeftMillis)
        Log.d("PomodoroTimer", "Timer paused with ${timeLeftMillis}ms remaining")
    }

    private fun stopTimer() {
        if (isTimerRunning || isPaused) {
            try {
                timer.cancel()
            } catch (e: Exception) {
                Log.e("PomodoroTimer", "Error cancelling timer: ${e.message}")
            }

            isTimerRunning = false
            _isRunningLiveData.postValue(false)
            isPaused = false
            _isPausedLiveData.postValue(false)

            if (!sessionFailed) {
                saveSession(false)
            }

            // Reset timer to original duration instead of 0
            timeLeftMillis = timerDurationMillis
            _timeLeftLiveData.postValue(timeLeftMillis)
            Log.d("PomodoroTimer", "Timer stopped, reset to original duration")

            // Unregister accelerometer properly
            try {
                sensorManager.unregisterListener(this)
            } catch (e: Exception) {
                Log.e("PomodoroTimer", "Error unregistering sensor: ${e.message}")
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun saveSession(completed: Boolean) {
        val durationMinutes = (timerDurationMillis / (1000 * 60)).toInt()
        val session = PomodoroSession(
            duration = durationMinutes,
            date = Date(),
            completed = completed
        )

        CoroutineScope(Dispatchers.IO).launch {
            repository.insertSession(session)
        }
        Log.d("PomodoroTimer", "Saved session: duration=${durationMinutes}min, completed=$completed")
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pomodoro Timer")
            .setContentText("Time remaining: ${formatTime(timeLeftMillis)}")
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pomodoro Timer")
            .setContentText(getString(R.string.pomodoro_completed))
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showFailureNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pomodoro Timer")
            .setContentText(getString(R.string.pomodoro_failed))
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(timeInMillis: Long): String {
        val minutes = (timeInMillis / 1000) / 60
        val seconds = (timeInMillis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && isTimerRunning) {
            // Check if we're still in the grace period
            if (System.currentTimeMillis() - timerStartTime < ACCELEROMETER_GRACE_PERIOD) {
                return // Ignore movement during grace period
            }

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate acceleration magnitude
            val acceleration = sqrt(x * x + y * y + z * z)

            // Check if the device was moved significantly
            if (acceleration > ACCELEROMETER_SENSITIVITY) {
                if (isTimerRunning && !sessionFailed) {
                    sessionFailed = true
                    _sessionFailedLiveData.postValue(true)
                    showFailureNotification()
                    saveSession(false)
                    Log.d("PomodoroTimer", "Session failed due to movement: acceleration=$acceleration")
                    stopTimer()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    // Add this method to get the total duration in minutes
    fun getTotalDurationMinutes(): Int {
        return (timerDurationMillis / (1000 * 60)).toInt()
    }

    // Add this method to check if timer was completed
    fun wasTimerCompleted(): Boolean {
        return timerCompleted
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTimerRunning) {
            try {
                timer.cancel()
            } catch (e: Exception) {
                Log.e("PomodoroTimer", "Error cancelling timer on destroy: ${e.message}")
            }
        }
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e("PomodoroTimer", "Error unregistering sensor on destroy: ${e.message}")
        }
        Log.d("PomodoroTimer", "Service destroyed")
    }
}