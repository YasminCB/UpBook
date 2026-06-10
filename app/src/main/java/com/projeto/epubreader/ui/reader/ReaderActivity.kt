package com.projeto.epubreader.ui.reader

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.projeto.epubreader.R
import com.projeto.epubreader.databinding.ActivityReaderBinding
import com.projeto.epubreader.ui.theme.ThemeManager
import kotlin.math.abs

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModels()
    private var isFullscreen = false
    private var lastLoadedHtml: String = ""
    private var lastLoadedBaseDir: String = ""
    private lateinit var gestureDetector: GestureDetector
    private var startX = 0f

    inner class FootnoteInterface {
        @android.webkit.JavascriptInterface
        fun showFootnote(content: String) {
            runOnUiThread { showFootnoteDialog(content) }
        }
    }

    private val indexLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val chapterIndex = result.data?.getIntExtra("CHAPTER_INDEX", -1) ?: -1
            if (chapterIndex >= 0) viewModel.loadChapter(chapterIndex)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.getThemeRes(this))
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val bookId = intent.getLongExtra("BOOK_ID", -1)
        if (bookId == -1L) { finish(); return }

        binding.toolbar.setNavigationIcon(R.drawable.ic_index)
        binding.toolbar.setNavigationOnClickListener {
            val i = Intent(this, IndexActivity::class.java)
            i.putExtra("BOOK_ID", bookId)
            indexLauncher.launch(i)
        }

        setupWebView()
        setupButtons()
        observeViewModel()

        binding.btnTheme.setOnClickListener {
            val current = viewModel.readerTheme.value ?: ReaderViewModel.ReaderTheme.DEFAULT
            val currentFont = viewModel.readerFont.value ?: "RobotoSlab"
            val currentFontSize = viewModel.readerFontSize.value ?: 18
            ThemeBottomSheet(
                currentTheme = current,
                currentFont = currentFont,
                currentFontSize = currentFontSize,
                onThemeSelected = { theme -> viewModel.applyTheme(theme) },
                onFontSelected = { font -> viewModel.applyFont(font) },
                onFontSizeSelected = { size -> viewModel.applyFontSize(size) }
            ).show(supportFragmentManager, "theme")
        }

        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }

        viewModel.loadBook(bookId)
        binding.root.post { toggleFullscreen() }
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    private fun setupWebView() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isFullscreen) toggleFullscreen()
                return true
            }
        })

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = true
            addJavascriptInterface(FootnoteInterface(), "AndroidFootnote")

            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startX = event.x
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.x - startX
                        if (abs(deltaX) > 150) {
                            val sy = scrollY
                            val contentH = (contentHeight * scale).toInt()
                            val visibleH = height
                            val atBottom = sy + visibleH >= contentH - 50
                            val atTop = sy <= 50

                            if (deltaX < 0 && atBottom) {
                                val current = viewModel.currentChapterIndex.value ?: 0
                                val total = viewModel.totalChapters.value ?: 0
                                if (current < total - 1) viewModel.loadChapter(current + 1)
                            } else if (deltaX > 0 && atTop) {
                                val current = viewModel.currentChapterIndex.value ?: 0
                                if (current > 0) viewModel.loadChapter(current - 1)
                            }
                        }
                    }
                }
                false
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val book = viewModel.currentBook.value
                    val percent = book?.currentScrollPercent ?: 0f
                    if (percent > 0f) {
                        binding.webView.evaluateJavascript(
                            "var h = document.body.scrollHeight - window.innerHeight; window.scrollTo(0, h * $percent);",
                            null
                        )
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()

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

                        val looksLikeNote = fragment.contains("note", ignoreCase = true) ||
                                fragment.contains("fn", ignoreCase = true) ||
                                fragment.contains("footnote", ignoreCase = true) ||
                                fragment.contains("endnote", ignoreCase = true) ||
                                fragment.contains("ref", ignoreCase = true) ||
                                filePart.contains("note", ignoreCase = true) ||
                                filePart.contains("fn", ignoreCase = true)

                        if (looksLikeNote) {
                            try {
                                if (targetFile.exists()) {
                                    val html = targetFile.readText()
                                    val content = extractAnchorContent(html, fragment)
                                    if (content.isNotBlank()) {
                                        showFootnoteDialog(content)
                                        return true
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("READER_LINK", "Erro nota: ${e.message}")
                            }
                        }

                        val chapterIndex = viewModel.getChapterIndexForFile(targetFile.absolutePath)
                        if (chapterIndex >= 0) {
                            viewModel.loadChapter(chapterIndex)
                            return true
                        }

                        try {
                            if (targetFile.exists()) {
                                val html = targetFile.readText()
                                val content = extractAnchorContent(html, fragment)
                                if (content.isNotBlank()) showFootnoteDialog(content)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("READER_LINK", "Erro fallback: ${e.message}")
                        }
                        return true
                    }

                    if (url.startsWith("epub-nav://")) return true
                    if (url.startsWith("file://")) return true
                    return false
                }
            }
        }
    }

    // ── Utilitários ───────────────────────────────────────────────────────────

    private fun extractAnchorContent(html: String, anchorId: String): String {
        val directPattern = Regex(
            """<(p|div|li|aside)[^>]*id=["\']${Regex.escape(anchorId)}["\'][^>]*>(.*?)</\1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        directPattern.find(html)?.let { return it.groupValues[2].trim() }

        val parentPattern = Regex(
            """<(p|div|li|aside)([^>]*)>((?:(?!</\1>).)*?<a[^>]*id=["\']${Regex.escape(anchorId)}["\'][^>]*>(?:(?!</\1>).)*?)</\1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        parentPattern.find(html)?.let { return it.groupValues[3].trim() }

        val fallback = Regex(
            """id=["\']${Regex.escape(anchorId)}["\'][^>]*>(.*?)</(?:p|div|li|aside|a)>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        fallback.find(html)?.let { return it.groupValues[1].trim() }

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
        viewModel.pageInfo.observe(this) { info ->
            binding.tvChapterInfo.text = info
        }
        viewModel.chapterContent.observe(this) { html ->
            val baseDir = viewModel.chapterBaseDir.value ?: return@observe
            lastLoadedHtml = html
            lastLoadedBaseDir = baseDir
            binding.webView.loadDataWithBaseURL(
                "file://$baseDir/", html, "text/html", "UTF-8", null
            )
        }
    }

    private fun showFootnoteDialog(content: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.behavior.isDraggable = false
        val theme = viewModel.readerTheme.value ?: ReaderViewModel.ReaderTheme.DEFAULT

        val webView = android.webkit.WebView(this).apply {
            settings.javaScriptEnabled = false
            isVerticalScrollBarEnabled = true
            scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
        }

        val html = """
            <html><head><style>
              body { font-family: Georgia, serif; font-size: 15px; padding: 16px;
                     line-height: 1.6; color: ${theme.textColor}; background: ${theme.backgroundColor}; }
              a { color: #1565C0; }
            </style></head><body>$content</body></html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            "file://${viewModel.chapterBaseDir.value}/", html, "text/html", "UTF-8", null
        )

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor(theme.backgroundColor))
        }
        val header = android.widget.RelativeLayout(this).apply { setPadding(48, 24, 24, 8) }
        val title = android.widget.TextView(this).apply {
            text = "📖 Nota de rodapé"; textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor(theme.textColor))
            layoutParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
            ).also { it.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START) }
        }
        val btnClose = android.widget.ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(android.graphics.Color.parseColor("#A020F0"))
            background = null
            layoutParams = android.widget.RelativeLayout.LayoutParams(48, 48).also {
                it.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            }
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(title)
        header.addView(btnClose)
        layout.addView(header)
        layout.addView(webView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 500
        ))
        dialog.setContentView(layout)
        dialog.show()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            supportActionBar?.hide()
            binding.toolbar.visibility = View.GONE
            binding.navBar.visibility = View.GONE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        } else {
            supportActionBar?.show()
            binding.toolbar.visibility = View.VISIBLE
            binding.navBar.visibility = View.VISIBLE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        binding.webView.evaluateJavascript(
            "(document.body.scrollHeight - window.innerHeight) > 0 ? window.scrollY / (document.body.scrollHeight - window.innerHeight) : 0"
        ) { result ->
            val percent = result?.toFloatOrNull() ?: 0f
            viewModel.saveProgress(binding.webView.scrollY, percent.coerceIn(0f, 1f))
        }
    }
}