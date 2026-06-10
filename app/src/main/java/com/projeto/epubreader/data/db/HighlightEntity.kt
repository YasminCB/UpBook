package com.projeto.epubreader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.projeto.epubreader.data.db.HighlightEntity

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val selectedText: String,
    val color: String, // "yellow", "blue", "green", "pink"
    val startOffset: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)