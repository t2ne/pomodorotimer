package com.example.pomodorotimer.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.example.pomodorotimer.data.PomodoroDatabase
import com.example.pomodorotimer.data.entity.PomodoroSession
import com.example.pomodorotimer.data.repository.PomodoroRepository

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PomodoroRepository
    val allSessions: LiveData<List<PomodoroSession>>
    val lastWeekSessions: LiveData<List<PomodoroSession>>
    val completedSessionsCount: LiveData<Int>
    val failedSessionsCount: LiveData<Int>
    val totalCompletedTime: LiveData<Long>

    init {
        val pomodoroDao = PomodoroDatabase.getDatabase(application).pomodoroSessionDao()
        repository = PomodoroRepository(pomodoroDao)
        allSessions = repository.allSessions
        lastWeekSessions = repository.getSessionsForLastWeek()
        completedSessionsCount = repository.completedSessionsCount
        failedSessionsCount = repository.failedSessionsCount
        totalCompletedTime = repository.totalCompletedTime
    }
}

