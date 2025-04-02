package com.example.pomodorotimer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "pomodoro_sessions")
data class PomodoroSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val duration: Int, // in minutes
    val date: Date,
    val completed: Boolean
)

