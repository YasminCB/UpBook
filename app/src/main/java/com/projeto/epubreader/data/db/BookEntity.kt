package com.projeto.epubreader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val filePath: String,       // caminho do .epub original
    val coverPath: String?,
    val extractedDir: String,   // pasta com HTML extraído
    val currentChapterIndex: Int = 0,
    val currentScrollY: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)