package com.example.pomodorotimer.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TimerViewModel : ViewModel() {

    private val _timerDuration = MutableLiveData<Int>()

    private val _isTimerRunning = MutableLiveData<Boolean>()
    val isTimerRunning: LiveData<Boolean> = _isTimerRunning

    private val _isPaused = MutableLiveData<Boolean>()
    val isPaused: LiveData<Boolean> = _isPaused

    init {
        _timerDuration.value = 25 // Default 25 minutes
        _isTimerRunning.value = false
        _isPaused.value = false
    }

    fun setTimerDuration(minutes: Int) {
        _timerDuration.value = minutes
    }

    fun startTimer() {
        _isTimerRunning.value = true
        _isPaused.value = false
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        _isPaused.value = true
    }

    fun stopTimer() {
        _isTimerRunning.value = false
        _isPaused.value = false
    }

    fun setTimerRunning(isRunning: Boolean) {
        _isTimerRunning.value = isRunning
    }
}

