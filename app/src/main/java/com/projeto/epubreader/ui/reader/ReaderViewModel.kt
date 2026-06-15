package com.projeto.epubreader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.projeto.epubreader.data.db.BookEntity
import com.projeto.epubreader.data.db.BookRepository
import com.projeto.epubreader.parser.EpubParser
import kotlinx.coroutines.launch
import java.io.File

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    private val parser = EpubParser(application)
    private val prefs = ReaderPreferences(application)

    val currentBook = MutableLiveData<BookEntity>()
    val currentChapterIndex = MutableLiveData(0)
    val chapterContent = MutableLiveData<String>()
    val totalChapters = MutableLiveData(0)
    val chapterBaseDir = MutableLiveData<String>()
    val currentChapterPath = MutableLiveData<String>()
    val pageInfo = MutableLiveData<String>()
    // Adicione essa variável no ViewModel
    var isChapterNavigation = false  // true = troca de capítulo (vai pro topo)

    val readerTheme = MutableLiveData(
        prefs.loadTheme().let { (bg, text, name) ->
            ReaderTheme(bg, text, name)
        }
    )

    val readerFont = MutableLiveData(prefs.loadFont())
    val readerFontSize = MutableLiveData(prefs.loadFontSize())

    private var _chapters: List<com.projeto.epubreader.parser.Chapter> = emptyList()

    data class ReaderTheme(
        val backgroundColor: String,
        val textColor: String,
        val name: String
    ) {
        companion object {
            val DEFAULT = ReaderTheme("#FFFFFF", "#222222", "Padrão")
            val SEPIA = ReaderTheme("#F5E6C8", "#3B2A1A", "Sépia")
            val DARK = ReaderTheme("#1A1A1A", "#E0E0E0", "Escuro")
            val GREEN = ReaderTheme("#1A2A1A", "#90EE90", "Verde")
            val CUSTOM = ReaderTheme("#FFFFFF", "#222222", "Personalizado")
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
        isChapterNavigation = true  // ← adicionar isso
        currentChapterIndex.value = chapterIndex
        loadChapter(book, chapterIndex)
    }

    private fun loadChapter(book: BookEntity, index: Int) = viewModelScope.launch {
        val epubBook = parser.parse(File(book.filePath))

        _chapters = epubBook.chapters

        val chapters = epubBook.chapters

        totalChapters.postValue(chapters.size)
        pageInfo.postValue("Parte ${index + 1} de ${chapters.size}")

        val chapter = chapters.getOrNull(index) ?: return@launch

        val chapterFile = File(chapter.filePath)
        val chapterDir = chapterFile.parent ?: epubBook.opfDir.absolutePath

        chapterBaseDir.postValue(chapterDir)
        currentChapterPath.postValue(chapterFile.absolutePath)

        val html = chapterFile.readText()
        val styledHtml = injectReadingStyles(html)
        chapterContent.postValue(styledHtml)
    }

    fun getChapterIndexForFile(filePath: String): Int {
        return _chapters.indexOfFirst { it.filePath == filePath }
    }

    fun applyTheme(theme: ReaderTheme) {
        prefs.saveTheme(theme.backgroundColor, theme.textColor, theme.name)
        readerTheme.value = theme
        val book = currentBook.value ?: return
        loadChapter(book, currentChapterIndex.value ?: 0)
    }

    fun applyFont(fontName: String) {
        prefs.saveFont(fontName)
        readerFont.value = fontName
        val book = currentBook.value ?: return
        loadChapter(book, currentChapterIndex.value ?: 0)
    }

    fun applyFontSize(size: Int) {
        prefs.saveFontSize(size)
        readerFontSize.value = size
        val book = currentBook.value ?: return
        loadChapter(book, currentChapterIndex.value ?: 0)
    }

    fun saveProgress(scrollY: Int, scrollPercent: Float) = viewModelScope.launch {
        val book = currentBook.value ?: return@launch
        val chapter = currentChapterIndex.value ?: 0
        repository.updateProgress(book.id, chapter, scrollY, scrollPercent)
    }

    fun addBookmark(scrollY: Int) = viewModelScope.launch {
        val book = currentBook.value ?: return@launch
        val chapter = currentChapterIndex.value ?: 0
        repository.addBookmark(book.id, chapter, scrollY)
    }

    private fun injectReadingStyles(html: String): String {
        val theme = readerTheme.value ?: ReaderTheme.DEFAULT
        val font = readerFont.value ?: "Georgia"
        val fontSize = readerFontSize.value ?: 18

        val css = """
            <style>
              @font-face { font-family: 'Inter'; src: url('file:///android_asset/fonts/inter.ttf'); }
              @font-face { font-family: 'Literata'; src: url('file:///android_asset/fonts/literata.ttf'); }
              @font-face { font-family: 'Merriweather'; src: url('file:///android_asset/fonts/merriweather.ttf'); }
              @font-face { font-family: 'OpenSans'; src: url('file:///android_asset/fonts/opensans.ttf'); }
              @font-face { font-family: 'RobotoSlab'; src: url('file:///android_asset/fonts/robotoslab.ttf'); }

              body {
                font-family: '$font', serif !important;
                font-size: ${fontSize}px !important;
                line-height: 1.8 !important;
                margin: 16px !important;
                color: ${theme.textColor} !important;
                background: ${theme.backgroundColor} !important;
                max-width: 680px !important;
              }

              * {
                color: ${theme.textColor} !important;
                background-color: ${theme.backgroundColor} !important;
              }

              ::-webkit-scrollbar { width: 6px; }
              ::-webkit-scrollbar-track { background: ${theme.backgroundColor}; }
              ::-webkit-scrollbar-thumb { background: #A020F0; border-radius: 3px; }

              img { max-width: 100% !important; height: auto !important; }

              a { color: #1565C0 !important; }
              a * { color: #1565C0 !important; }
            </style>
        """.trimIndent()

        val script = """
            <script>
            document.addEventListener('DOMContentLoaded', function() {
                document.querySelectorAll('a[href]').forEach(function(link) {
                    var href = link.getAttribute('href');
                    if (!href || href.indexOf('://') !== -1) return;

                    link.addEventListener('click', function(e) {
                        e.preventDefault();
                        console.log("EPUB LINK:", href);

                        if (href.startsWith('#')) {
                            var targetId = href.substring(1);
                            var target = document.getElementById(targetId);
                            if (target) AndroidFootnote.showFootnote(target.innerHTML);
                            return;
                        }

                        var parts = href.split('#');
                        var filePart = parts[0];
                        var fragment = parts.length > 1 ? parts[1] : null;

                        if (filePart && fragment) {
                            window.location.href = 'epub-link://' + href;
                            return;
                        }

                        if (filePart && !fragment) {
                            window.location.href = 'epub-nav://' + href;
                            return;
                        }
                    });
                });
            });
            </script>
        """.trimIndent()

        val headInsert = css + script

        return if (html.contains("<head>", ignoreCase = true)) {
            html.replace("<head>", "<head>$headInsert", ignoreCase = true)
        } else {
            "<html><head>$headInsert</head><body>$html</body></html>"
        }
    }
}