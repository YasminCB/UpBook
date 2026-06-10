package com.projeto.epubreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class, HighlightEntity::class],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Cria tabela nova
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

                // 2. Copia os dados
                database.execSQL("""
                    INSERT INTO books_new (
                        id, title, author, filePath, coverPath, extractedDir,
                        currentChapterIndex, currentScrollY, totalChapters, addedAt
                    )
                    SELECT
                        id, title, author, filePath, coverPath, extractedDir,
                        currentChapterIndex, currentScrollY,
                        CASE WHEN totalChapters IS NULL THEN 0 ELSE totalChapters END,
                        addedAt
                    FROM books
                """.trimIndent())

                // 3. Remove antiga e renomeia
                database.execSQL("DROP TABLE books")
                database.execSQL("ALTER TABLE books_new RENAME TO books")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE books ADD COLUMN currentScrollPercent REAL NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS highlights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        selectedText TEXT NOT NULL,
                        color TEXT NOT NULL,
                        startOffset INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "epub_reader_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}