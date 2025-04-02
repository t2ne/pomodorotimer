package com.example.pomodorotimer.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pomodorotimer.data.entity.PomodoroSession
import java.util.Date

@Dao
interface PomodoroSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PomodoroSession)

    @Query("SELECT * FROM pomodoro_sessions ORDER BY date DESC")
    fun getAllSessions(): LiveData<List<PomodoroSession>>

    @Query("SELECT * FROM pomodoro_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getSessionsInDateRange(startDate: Date, endDate: Date): LiveData<List<PomodoroSession>>

    @Query("SELECT COUNT(*) FROM pomodoro_sessions WHERE completed = 1")
    fun getCompletedSessionsCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM pomodoro_sessions WHERE completed = 0")
    fun getFailedSessionsCount(): LiveData<Int>

    @Query("SELECT SUM(duration) FROM pomodoro_sessions WHERE completed = 1")
    fun getTotalCompletedTime(): LiveData<Long>
}
