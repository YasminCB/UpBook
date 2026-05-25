package com.projeto.epubreader.ui.reader

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.projeto.epubreader.databinding.ActivityReaderBinding

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModels()

    // ← AQUI, fora de qualquer função
    inner class FootnoteInterface {
        @android.webkit.JavascriptInterface
        fun showFootnote(content: String) {
            runOnUiThread {
                showFootnoteDialog(content)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val bookId = intent.getLongExtra("BOOK_ID", -1)
        if (bookId == -1L) { finish(); return }

        setupWebView()
        setupButtons()
        observeViewModel()
        binding.btnTheme.setOnClickListener {
            val current = viewModel.readerTheme.value ?: ReaderViewModel.ReaderTheme.DEFAULT
            ThemeBottomSheet(current) { theme ->
                viewModel.applyTheme(theme)
            }.show(supportFragmentManager, "theme")
        }
        viewModel.loadBook(bookId)
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = true
            addJavascriptInterface(FootnoteInterface(), "AndroidFootnote")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    android.util.Log.d("READER_LINK", "URL: $url")

                    if (url.startsWith("epub-link://")) {
                        val href = url.removePrefix("epub-link://")
                        val baseDir = viewModel.chapterBaseDir.value ?: return true

                        val parts = href.split("#")
                        val filePart = parts.getOrNull(0) ?: ""
                        val fragment = parts.getOrNull(1) ?: return true

                        val targetFile = if (filePart.isBlank()) {
                            java.io.File(viewModel.currentChapterPath.value ?: return true)
                        } else {
                            java.io.File(baseDir, filePart)
                        }

                        android.util.Log.d("READER_LINK", "Nota em: ${targetFile.absolutePath}, id: $fragment")

                        try {
                            if (targetFile.exists()) {
                                val html = targetFile.readText()
                                val content = extractAnchorContent(html, fragment)
                                android.util.Log.d("READER_LINK", "Conteúdo: $content")
                                if (content.isNotBlank()) {
                                    showFootnoteDialog(content)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("READER_LINK", "Erro: ${e.message}")
                        }
                        return true
                    }

                    if (url.startsWith("epub-nav://")) {
                        android.util.Log.d("READER_LINK", "Navegação ignorada: $url")
                        return true
                    }

                    if (url.startsWith("file://")) return true

                    return false
                }
            }
        }
    }

    private fun extractAnchorContent(html: String, anchorId: String): String {
        val paragraphPattern = Regex(
            """<(p|div|li)[^>]*id=["\']${Regex.escape(anchorId)}["\'][^>]*>(.*?)</\1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        paragraphPattern.find(html)?.let {
            return it.groupValues[2].trim()
        }

        val fallback = Regex(
            """id=["\']${Regex.escape(anchorId)}["\'][^>]*>(.*?)</(?:p|div|li|a)>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        fallback.find(html)?.let {
            return it.groupValues[1].trim()
        }

        return ""
    }

    private fun setupButtons() {
        binding.btnPrevChapter.setOnClickListener {
            val current = viewModel.currentChapterIndex.value ?: 0
            if (current > 0) viewModel.loadChapter(current - 1)
        }

        binding.btnNextChapter.setOnClickListener {
            val current = viewModel.currentChapterIndex.value ?: 0
            val total = viewModel.totalChapters.value ?: 0
            if (current < total - 1) viewModel.loadChapter(current + 1)
        }
    }

    private fun observeViewModel() {
        viewModel.currentBook.observe(this) { book ->
            supportActionBar?.title = book.title
        }

        viewModel.currentChapterIndex.observe(this) { index ->
            val total = viewModel.totalChapters.value ?: 0
            binding.tvChapterInfo.text = "Cap. ${index + 1} / $total"
        }

        viewModel.totalChapters.observe(this) { total ->
            val index = viewModel.currentChapterIndex.value ?: 0
            binding.tvChapterInfo.text = "Cap. ${index + 1} / $total"
        }

        viewModel.chapterContent.observe(this) { html ->
            val baseDir = viewModel.chapterBaseDir.value ?: return@observe
            binding.webView.loadDataWithBaseURL(
                "file://$baseDir/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    private fun showFootnoteDialog(content: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val webView = android.webkit.WebView(this)
        webView.settings.javaScriptEnabled = false

        val theme = viewModel.readerTheme.value ?: ReaderViewModel.ReaderTheme.DEFAULT
        val html = """
            <html><head>
            <style>
              body { font-family: Georgia, serif; font-size: 15px;
                     padding: 16px; line-height: 1.6;
                     color: ${theme.textColor};
                     background: ${theme.backgroundColor}; }
              a { color: #1565C0; }
            </style>
            </head><body>$content</body></html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            "file://${viewModel.chapterBaseDir.value}/",
            html, "text/html", "UTF-8", null
        )

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        val title = android.widget.TextView(this).apply {
            text = "📖 Nota de rodapé"
            textSize = 15f
            setPadding(48, 32, 48, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val btnClose = com.google.android.material.button.MaterialButton(this).apply {
            text = "Fechar"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(32, 8, 32, 24) }
            setOnClickListener { dialog.dismiss() }
        }

        val wvParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 500
        )

        layout.addView(title)
        layout.addView(webView, wvParams)
        layout.addView(btnClose)

        dialog.setContentView(layout)
        dialog.show()
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveProgress(binding.webView.scrollY)
    }
}