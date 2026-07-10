package com.zhixing.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 极简 service locator：单例持有 AppDatabase。
 *
 * MainActivity 通过它拿 DAO。后续有需要可升级为 DI 框架（Hilt/Koin），
 * 当前 MVP 阶段保持轻量。
 */
object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    // v3 → v4: tasks 表加 reviewText 列（回顾文本持久化）
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN reviewText TEXT NOT NULL DEFAULT ''")
        }
    }

    fun db(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "zhixing.db",
            ).addMigrations(MIGRATION_3_4).build().also { instance = it }
        }
    }
}
