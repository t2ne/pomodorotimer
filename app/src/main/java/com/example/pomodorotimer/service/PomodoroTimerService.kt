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
import android.os.IBinder
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

    private var timerDurationMillis = 0L
    private var timeLeftMillis = 0L
    private var endTime = 0L

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
                    if (isFirstRun) {
                        timerDurationMillis = it.getLongExtra(Constants.EXTRA_TIMER_DURATION, 25 * 60 * 1000L)
                        isFirstRun = false
                    }
                    if (isPaused) {
                        startTimer()
                        isPaused = false
                        _isPausedLiveData.postValue(false)
                    } else {
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

        timeLeftMillis = timerDurationMillis
        _timeLeftLiveData.postValue(timeLeftMillis)

        startTimer()

        // Register accelerometer only after calling startForeground()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
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
        if (timeLeftMillis <= 0) { // Reset if it's zero
            timeLeftMillis = timerDurationMillis
        }

        endTime = System.currentTimeMillis() + timeLeftMillis

        timer = object : CountDownTimer(timeLeftMillis, Constants.TIMER_UPDATE_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMillis = millisUntilFinished
                _timeLeftLiveData.postValue(timeLeftMillis)
                updateNotification()
            }

            override fun onFinish() {
                timeLeftMillis = 0L
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
        timer.cancel()
        isPaused = true
        _isPausedLiveData.postValue(true)
        isTimerRunning = false
        _isRunningLiveData.postValue(false)
        timeLeftMillis = endTime - System.currentTimeMillis()
        _timeLeftLiveData.postValue(timeLeftMillis)
    }

    private fun stopTimer() {
        if (isTimerRunning || isPaused) {
            timer.cancel()
            isTimerRunning = false
            _isRunningLiveData.postValue(false)
            isPaused = false
            _isPausedLiveData.postValue(false)

            if (!sessionFailed) {
                saveSession(false)
            }

            // Reset timer duration
            timeLeftMillis = 0L
            _timeLeftLiveData.postValue(timeLeftMillis)

            // Unregister accelerometer properly
            sensorManager.unregisterListener(this)

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
                    stopTimer()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTimerRunning) {
            timer.cancel()
        }
        sensorManager.unregisterListener(this)
    }
}
