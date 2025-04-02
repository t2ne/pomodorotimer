package com.example.pomodorotimer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pomodorotimer.data.dao.PomodoroSessionDao
import com.example.pomodorotimer.data.entity.PomodoroSession
import com.example.pomodorotimer.util.DateConverter

@Database(entities = [PomodoroSession::class], version = 1, exportSchema = false)
@TypeConverters(DateConverter::class)
abstract class PomodoroDatabase : RoomDatabase() {

    abstract fun pomodoroSessionDao(): PomodoroSessionDao

    companion object {
        @Volatile
        private var INSTANCE: PomodoroDatabase? = null

        fun getDatabase(context: Context): PomodoroDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PomodoroDatabase::class.java,
                    "pomodoro_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

