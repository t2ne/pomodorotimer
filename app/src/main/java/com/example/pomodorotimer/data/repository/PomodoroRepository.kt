package com.example.pomodorotimer.data.repository

import androidx.lifecycle.LiveData
import com.example.pomodorotimer.data.dao.PomodoroSessionDao
import com.example.pomodorotimer.data.entity.PomodoroSession
import java.util.Calendar

class PomodoroRepository(private val pomodoroSessionDao: PomodoroSessionDao) {

    val allSessions: LiveData<List<PomodoroSession>> = pomodoroSessionDao.getAllSessions()
    val completedSessionsCount: LiveData<Int> = pomodoroSessionDao.getCompletedSessionsCount()
    val failedSessionsCount: LiveData<Int> = pomodoroSessionDao.getFailedSessionsCount()
    val totalCompletedTime: LiveData<Long> = pomodoroSessionDao.getTotalCompletedTime()

    suspend fun insertSession(session: PomodoroSession) {
        pomodoroSessionDao.insertSession(session)
    }

    fun getSessionsForLastWeek(): LiveData<List<PomodoroSession>> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time

        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startDate = calendar.time

        return pomodoroSessionDao.getSessionsInDateRange(startDate, endDate)
    }
}

