package com.ouvinte.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ouvinte.app.data.local.dao.ProjectDao
import com.ouvinte.app.data.local.dao.SessionDao
import com.ouvinte.app.data.local.entity.*

@Database(
    entities = [
        ProjectEntity::class,
        SessionEntity::class,
        SpeakerEntity::class,
        TranscriptSegmentEntity::class,
        AnalysisEntity::class,
        QuestionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun projectDao(): ProjectDao

    companion object {
        const val DATABASE_NAME = "ouvinte.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN geminiFileUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN geminiFileName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS projects (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE sessions ADD COLUMN projectId INTEGER DEFAULT NULL")
            }
        }
    }
}
