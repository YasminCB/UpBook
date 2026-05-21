package com.projeto.epubreader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.projeto.epubreader.data.db.BookRepository
import com.projeto.epubreader.data.db.BookEntity
import com.projeto.epubreader.parser.EpubParser
import kotlinx.coroutines.launch
import java.io.File

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    private val parser = EpubParser(application)

    val currentBook = MutableLiveData<BookEntity>()
    val currentChapterIndex = MutableLiveData<Int>(0)
    val chapterContent = MutableLiveData<String>()
    val totalChapters = MutableLiveData<Int>(0)
    val chapterBaseDir = MutableLiveData<String>()

    val readerTheme = MutableLiveData<ReaderTheme>(ReaderTheme.DEFAULT)

    data class ReaderTheme(
        val backgroundColor: String,
        val textColor: String,
        val name: String
    ) {
        companion object {
            val DEFAULT    = ReaderTheme("#FFFFFF", "#222222", "Padrão")
            val SEPIA      = ReaderTheme("#F5E6C8", "#3B2A1A", "Sépia")
            val DARK       = ReaderTheme("#1A1A1A", "#E0E0E0", "Escuro")
            val AMOLED     = ReaderTheme("#000000", "#FFFFFF", "AMOLED")
            val GREEN      = ReaderTheme("#1A2A1A", "#90EE90", "Verde")
            val CUSTOM     = ReaderTheme("#FFFFFF", "#222222", "Personalizado")
        }
    }

    fun loadBook(bookId: Long) = viewModelScope.launch {
        val book = repository.getBook(bookId) ?: return@launch
        currentBook.value = book
        currentChapterIndex.value = book.currentChapterIndex
        loadChapter(book, book.currentChapterIndex)
    }

    fun loadChapter(chapterIndex: Int) {
        val book = currentBook.value ?: return
        currentChapterIndex.value = chapterIndex
        loadChapter(book, chapterIndex)
    }

    private fun loadChapter(book: BookEntity, index: Int) = viewModelScope.launch {
        val epubBook = parser.parse(File(book.filePath))
        val chapters = epubBook.chapters
        totalChapters.postValue(chapters.size)
        val chapter = chapters.getOrNull(index) ?: return@launch

        val chapterFile = File(chapter.filePath)
        val chapterDir = chapterFile.parent ?: epubBook.opfDir.absolutePath  // ← pasta do capítulo

        chapterBaseDir.postValue(chapterDir)   // ← novo LiveData
        val html = chapterFile.readText()
        val styledHtml = injectReadingStyles(html, chapterDir)
        chapterContent.postValue(styledHtml)
    }


    fun saveProgress(scrollY: Int) = viewModelScope.launch {
        val book = currentBook.value ?: return@launch
        val chapter = currentChapterIndex.value ?: 0
        repository.updateProgress(book.id, chapter, scrollY)
    }

    fun addBookmark(scrollY: Int) = viewModelScope.launch {
        val book = currentBook.value ?: return@launch
        val chapter = currentChapterIndex.value ?: 0
        repository.addBookmark(book.id, chapter, scrollY)
    }

    private fun injectReadingStyles(html: String, baseDir: String): String {
        val theme = readerTheme.value ?: ReaderTheme.DEFAULT
        val css = """
        <style>
          body { 
            font-family: Georgia, serif; 
            font-size: 18px; 
            line-height: 1.8; 
            margin: 16px; 
            color: ${theme.textColor}; 
            background: ${theme.backgroundColor};
            max-width: 680px;
          }
          img { max-width: 100%; height: auto; }
          a { color: #1565C0; }
        </style>
    """.trimIndent()

        return if (html.contains("<head>", ignoreCase = true)) {
            html.replace("<head>", "<head>$css", ignoreCase = true)
        } else {
            "<html><head>$css</head><body>$html</body></html>"
        }
    }
    fun applyTheme(theme: ReaderTheme) {
        readerTheme.value = theme
        // Recarrega o capítulo atual com o novo tema
        val book = currentBook.value ?: return
        val index = currentChapterIndex.value ?: 0
        loadChapter(book, index)
    }
}