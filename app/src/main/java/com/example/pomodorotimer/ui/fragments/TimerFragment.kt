package com.example.pomodorotimer.ui.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.pomodorotimer.R
import com.example.pomodorotimer.databinding.FragmentTimerBinding
import com.example.pomodorotimer.service.PomodoroTimerService
import com.example.pomodorotimer.ui.MainActivity
import com.example.pomodorotimer.ui.viewmodels.TimerViewModel
import com.example.pomodorotimer.util.Constants.ACTION_PAUSE_SERVICE
import com.example.pomodorotimer.util.Constants.ACTION_START_OR_RESUME_SERVICE
import com.example.pomodorotimer.util.Constants.ACTION_STOP_SERVICE
import com.example.pomodorotimer.util.Constants.EXTRA_TIMER_DURATION
import me.tankery.lib.circularseekbar.CircularSeekBar
import java.util.Locale

class TimerFragment : Fragment() {

    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TimerViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private val PREF_NAME = "PomodoroPrefs"
    private val KEY_TIMER_DURATION = "timer_duration"
    private val DEFAULT_DURATION = 25f

    private var timerService: PomodoroTimerService? = null
    private var isBound = false
    private var isUpdatingSeekBar = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PomodoroTimerService.LocalBinder
            timerService = binder.getService()
            isBound = true

            // Initialize UI with current service state
            initializeUIFromService()

            // Observe service state
            timerService?.timeLeftLiveData?.observe(viewLifecycleOwner) { timeLeft ->
                updateTimerDisplay(timeLeft)
            }

            timerService?.isRunningLiveData?.observe(viewLifecycleOwner) { isRunning ->
                updateButtonState(isRunning)
                binding.circularSeekBar.isEnabled = !isRunning
            }

            timerService?.isPausedLiveData?.observe(viewLifecycleOwner) { isPaused ->
                if (isPaused) {
                    binding.btnStartStop.text = getString(R.string.resume)
                }
            }

            timerService?.sessionFailedLiveData?.observe(viewLifecycleOwner) { failed ->
                if (failed) {
                    binding.btnStartStop.text = getString(R.string.start)
                    binding.circularSeekBar.isEnabled = true

                    // Reset seekbar to initial value
                    val initialMinutes = timerService?.getTotalDurationMinutes() ?: 25
                    updateSeekBarAndDisplay(initialMinutes.toFloat())
                }
            }

            timerService?.timerCompletedLiveData?.observe(viewLifecycleOwner) { completed ->
                if (completed) {
                    binding.btnStartStop.text = getString(R.string.start)
                    binding.circularSeekBar.isEnabled = true

                    // Reset seekbar to initial value
                    val initialMinutes = timerService?.getTotalDurationMinutes() ?: 25
                    updateSeekBarAndDisplay(initialMinutes.toFloat())
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        viewModel = ViewModelProvider(this)[TimerViewModel::class.java]

        setupUI()
        setupListeners()
    }

    private fun initializeUIFromService() {
        timerService?.let { service ->
            // Get current time left
            val timeLeft = service.timeLeftLiveData.value ?: 0L

            // If service has a valid time, use it
            if (timeLeft > 0) {
                // Update timer display
                updateTimerDisplay(timeLeft)

                // Update button state
                val isRunning = service.isRunningLiveData.value ?: false
                updateButtonState(isRunning)
                binding.circularSeekBar.isEnabled = !isRunning

                // Update seekbar position
                if (!isRunning) {
                    val minutes = service.getTotalDurationMinutes().toFloat()
                    updateSeekBarAndDisplay(minutes)
                }
            } else {
                // If service doesn't have a valid time, use saved preferences
                val savedDuration = getSavedDuration()
                updateSeekBarAndDisplay(savedDuration)
            }
        } ?: run {
            // If service is null, use saved preferences
            val savedDuration = getSavedDuration()
            updateSeekBarAndDisplay(savedDuration)
        }
    }

    private fun setupUI() {
        // Get saved duration from SharedPreferences
        val savedDuration = getSavedDuration()

        // Set up circular seek bar
        binding.circularSeekBar.apply {
            // Set minimum value programmatically since app:cs_min is not supported in XML
            //setMin(1f)
            progress = savedDuration // Use saved duration instead of hardcoded 25

            setOnSeekBarChangeListener(object : CircularSeekBar.OnCircularSeekBarChangeListener {
                override fun onProgressChanged(
                    circularSeekBar: CircularSeekBar?,
                    progress: Float,
                    fromUser: Boolean
                ) {
                    if (fromUser && !isUpdatingSeekBar) {
                        val minutes = progress.toInt()
                        binding.tvTimer.text = String.format(Locale.getDefault(), "%02d:00", minutes)
                        viewModel.setTimerDuration(minutes)

                        // Save the new duration to SharedPreferences
                        saveDuration(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: CircularSeekBar?) {
                    // Not needed for this implementation
                }

                override fun onStopTrackingTouch(seekBar: CircularSeekBar?) {
                    // Not needed for this implementation
                }
            })
        }

        // Set initial timer display
        binding.tvTimer.text = String.format(Locale.getDefault(), "%02d:00", savedDuration.toInt())
    }

    private fun setupListeners() {
        // Menu button opens drawer
        binding.btnMenu.setOnClickListener {
            (activity as MainActivity).openDrawer()
        }

        // Start/Stop button
        binding.btnStartStop.setOnClickListener {
            if (viewModel.isTimerRunning.value == true) {
                sendCommandToService(ACTION_STOP_SERVICE)
            } else if (viewModel.isPaused.value == true) {
                sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
            } else {
                startTimer()
            }
        }
    }

    private fun startTimer() {
        val durationMinutes = binding.circularSeekBar.progress.toInt()
        val durationMillis = durationMinutes * 60 * 1000L

        // Save the duration to SharedPreferences
        saveDuration(durationMinutes.toFloat())

        // Add logging to verify the correct value
        Log.d("TimerFragment", "Starting timer with duration: $durationMinutes minutes ($durationMillis ms)")

        Intent(requireContext(), PomodoroTimerService::class.java).apply {
            action = ACTION_START_OR_RESUME_SERVICE
            putExtra(EXTRA_TIMER_DURATION, durationMillis)
            requireContext().startForegroundService(this)
        }

        viewModel.startTimer()
        binding.btnStartStop.text = getString(R.string.stop)
        binding.circularSeekBar.isEnabled = false
    }

    private fun sendCommandToService(action: String) {
        Intent(requireContext(), PomodoroTimerService::class.java).apply {
            this.action = action
            requireContext().startForegroundService(this)
        }

        if (action == ACTION_STOP_SERVICE) {
            viewModel.stopTimer()
            binding.btnStartStop.text = getString(R.string.start)
            binding.circularSeekBar.isEnabled = true
        } else if (action == ACTION_PAUSE_SERVICE) {
            viewModel.pauseTimer()
            binding.btnStartStop.text = getString(R.string.resume)
        }
    }

    private fun updateTimerDisplay(timeLeftMillis: Long) {
        val minutes = (timeLeftMillis / 1000) / 60
        val seconds = (timeLeftMillis / 1000) % 60
        binding.tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        // Update the seekbar progress to match the remaining time
        if (timerService?.isRunningLiveData?.value == true) {
            isUpdatingSeekBar = true
            val minutesLeft = minutes.toFloat()
            val secondsPercentage = seconds.toFloat() / 60f
            val progressValue = minutesLeft + secondsPercentage

            // Only update if timer is running
            binding.circularSeekBar.progress = progressValue
            isUpdatingSeekBar = false
        }
    }

    private fun updateSeekBarAndDisplay(minutes: Float) {
        isUpdatingSeekBar = true
        binding.circularSeekBar.progress = minutes
        binding.tvTimer.text = String.format(Locale.getDefault(), "%02d:00", minutes.toInt())
        isUpdatingSeekBar = false
    }

    private fun updateButtonState(isRunning: Boolean) {
        viewModel.setTimerRunning(isRunning)
        binding.btnStartStop.text = if (isRunning) getString(R.string.stop) else getString(R.string.start)
    }

    // Save the duration to SharedPreferences
    private fun saveDuration(minutes: Float) {
        sharedPreferences.edit().putFloat(KEY_TIMER_DURATION, minutes).apply()
        Log.d("TimerFragment", "Saved duration: $minutes minutes")
    }

    // Get the saved duration from SharedPreferences
    private fun getSavedDuration(): Float {
        val savedDuration = sharedPreferences.getFloat(KEY_TIMER_DURATION, DEFAULT_DURATION)
        Log.d("TimerFragment", "Retrieved saved duration: $savedDuration minutes")
        return savedDuration
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI when fragment is resumed
        if (isBound) {
            initializeUIFromService()
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to the service
        Intent(requireContext(), PomodoroTimerService::class.java).also { intent ->
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from the service
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}