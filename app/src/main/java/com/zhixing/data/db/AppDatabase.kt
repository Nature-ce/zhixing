package com.zhixing.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.data.entity.TaskEntity

@Database(
    entities = [TaskEntity::class, SubprojectEntity::class, ScheduleEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subprojectDao(): SubprojectDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subprojects ADD COLUMN completedAt INTEGER")
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "zhixing.db",
            )
                .addMigrations(MIGRATION_4_5)
                .build()
        }
    }
}
