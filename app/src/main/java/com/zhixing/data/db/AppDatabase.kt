package com.zhixing.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.TaskEntity

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
