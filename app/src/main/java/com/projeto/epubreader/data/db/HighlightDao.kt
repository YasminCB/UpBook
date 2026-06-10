package com.projeto.epubreader.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getByBook(bookId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND color = :color ORDER BY createdAt DESC")
    fun getByBookAndColor(bookId: Long, color: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun getByChapter(bookId: Long, chapterIndex: Int): List<HighlightEntity>

    @Delete
    suspend fun delete(highlight: HighlightEntity)
}