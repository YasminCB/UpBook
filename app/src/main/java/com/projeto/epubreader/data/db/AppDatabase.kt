package com.projeto.epubreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class, BookmarkEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE books ADD COLUMN totalChapters INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recria a tabela com o schema correto
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS books_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        coverPath TEXT,
                        extractedDir TEXT NOT NULL,
                        currentChapterIndex INTEGER NOT NULL DEFAULT 0,
                        currentScrollY INTEGER NOT NULL DEFAULT 0,
                        totalChapters INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO books_new (id, title, author, filePath, coverPath, extractedDir, currentChapterIndex, currentScrollY, totalChapters, addedAt)
                    SELECT id, title, author, filePath, coverPath, extractedDir, currentChapterIndex, currentScrollY, 
                    CASE WHEN totalChapters IS NULL THEN 0 ELSE totalChapters END,
                    addedAt FROM books
                """.trimIndent())
                database.execSQL("DROP TABLE books")
                database.execSQL("ALTER TABLE books_new RENAME TO books")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "epub_reader_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}