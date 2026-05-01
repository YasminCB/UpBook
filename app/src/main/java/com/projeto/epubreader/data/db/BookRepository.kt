package com.projeto.epubreader.data.db

import android.content.Context
import android.net.Uri
import com.projeto.epubreader.parser.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).bookDao()
    private val parser = EpubParser(context)

    val allBooks = dao.getAllBooks()

    suspend fun importBook(uri: Uri): Long = withContext(Dispatchers.IO) {
        // Copia o arquivo para armazenamento interno
        val fileName = "book_${System.currentTimeMillis()}.epub"
        val destFile = File(context.filesDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }

        // Faz o parse
        val book = parser.parse(destFile)

        // Salva no banco
        val entity = BookEntity(
            title = book.title,
            author = book.author,
            filePath = destFile.absolutePath,
            coverPath = book.coverPath,
            extractedDir = book.extractedDir.absolutePath
        )
        dao.insertBook(entity)
    }

    suspend fun getBook(id: Long) = dao.getBookById(id)

    suspend fun updateProgress(bookId: Long, chapterIndex: Int, scrollY: Int) {
        withContext(Dispatchers.IO) {
            val book = dao.getBookById(bookId) ?: return@withContext
            dao.updateBook(book.copy(
                currentChapterIndex = chapterIndex,
                currentScrollY = scrollY
            ))
        }
    }

    fun getBookmarks(bookId: Long) = dao.getBookmarks(bookId)

    suspend fun addBookmark(bookId: Long, chapterIndex: Int, scrollY: Int) {
        withContext(Dispatchers.IO) {
            dao.insertBookmark(BookmarkEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                scrollY = scrollY
            ))
        }
    }

    suspend fun deleteBook(bookId: Long) = withContext(Dispatchers.IO) {
        val book = dao.getBookById(bookId) ?: return@withContext
        // Remove arquivos do disco
        File(book.extractedDir).deleteRecursively()
        File(book.filePath).delete()
        dao.deleteBook(book)
    }
}