package com.projeto.epubreader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val scrollY: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)