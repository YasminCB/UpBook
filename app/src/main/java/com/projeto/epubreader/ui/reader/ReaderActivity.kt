package com.projeto.epubreader.ui.reader

import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
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

    private val highlightColors = listOf(
        "#FFD700",
        "#00BFFF",
        "#00FF7F",
        "#DC143C"
    )
    private val highlightEmojis = listOf("🟡", "🔵", "🟢", "🔴")

    private var pendingSelectedText: String = ""

    inner class FootnoteInterface {
        @android.webkit.JavascriptInterface
        fun showFootnote(content: String) {
            runOnUiThread { showFootnoteDialog(content) }
        }
    }

    inner class SelectionInterface {
        @android.webkit.JavascriptInterface
        fun onTextSelected(text: String) {
            pendingSelectedText = text
        }

        @android.webkit.JavascriptInterface
        fun onHighlightTapped(spanId: String, color: String) {
            runOnUiThread { showHighlightOptions(spanId, color) }
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

    // ── Intercepta o ActionMode nativo de seleção de texto ───────────────────

    override fun onActionModeStarted(mode: ActionMode) {
        if (mode.menu.findItem(1000) == null) {
            highlightColors.forEachIndexed { i, hex ->
                val item = mode.menu.add(
                    Menu.NONE,
                    1000 + i,
                    Menu.NONE,
                    "${highlightEmojis[i]} Marcar"
                )

                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                item.setOnMenuItemClickListener {
                    mode.finish()
                    applyHighlight(hex)
                    true
                }
            }
        }
        super.onActionModeStarted(mode)
    }

    // ── JS injetado após carregamento ─────────────────────────────────────────

    private val selectionJs = """
        (function() {
            if (window._readerListenersAttached) return;
            window._readerListenersAttached = true;

            document.addEventListener('selectionchange', function() {
                clearTimeout(window._selTimer);
                window._selTimer = setTimeout(function() {
                    var sel = window.getSelection();
                    var text = sel ? sel.toString().trim() : '';
                    if (typeof AndroidSelection !== 'undefined') {
                        AndroidSelection.onTextSelected(text);
                    }
                }, 200);
            });

            document.addEventListener('click', function(e) {
                var el = e.target;
                while (el && el !== document.body) {
                    if (el.dataset && el.dataset.highlightId) {
                        if (typeof AndroidSelection !== 'undefined') {
                            AndroidSelection.onHighlightTapped(
                                el.dataset.highlightId,
                                el.style.backgroundColor
                            );
                        }
                        return;
                    }
                    el = el.parentElement;
                }
            });
        })();
    """.trimIndent()

    // ── Highlight: aplicar ────────────────────────────────────────────────────

    private fun applyHighlight(colorHex: String) {
        val spanId = "hl_${System.currentTimeMillis()}"

        val js = """
            (function() {
                var sel = window.getSelection();
                if (!sel || sel.rangeCount === 0 || sel.toString().trim() === '') {
                    return JSON.stringify({ok: false, text: ''});
                }
                var text = sel.toString();
                var range = sel.getRangeAt(0);
                var span = document.createElement('span');
                span.style.backgroundColor = '$colorHex';
                span.style.color = '#000000';
                span.dataset.highlightId = '$spanId';
                span.style.borderRadius = '2px';
                try {
                    range.surroundContents(span);
                } catch(e) {
                    var frag = range.extractContents();
                    span.appendChild(frag);
                    range.insertNode(span);
                }
                sel.removeAllRanges();
                return JSON.stringify({ok: true, text: text});
            })()
        """.trimIndent()

        binding.webView.evaluateJavascript(js) { result ->
            try {
                val clean = result?.trim('"')?.replace("\\\"", "\"") ?: return@evaluateJavascript
                val json = org.json.JSONObject(clean)
                if (json.optBoolean("ok")) {
                    val text = json.optString("text")
                    if (text.isNotBlank()) {
                        val bookId = intent.getLongExtra("BOOK_ID", -1)
                        val chapterIndex = viewModel.currentChapterIndex.value ?: 0
                        viewModel.saveHighlight(bookId, chapterIndex, text, colorHex)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HIGHLIGHT", "Erro ao parsear resultado: ${e.message}")
            }
        }
    }

    // ── Highlight: opções ao tocar num marcado ────────────────────────────────

    private fun showHighlightOptions(spanId: String, colorRgb: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 24, 32, 32)
        }

        val title = android.widget.TextView(this).apply {
            text = "Marcação"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        val colorRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        highlightColors.forEachIndexed { i, hex ->
            val circle = View(this).apply {
                val size = (40 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = (12 * resources.displayMetrics.density).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(hex))
                    setStroke(
                        (2 * resources.displayMetrics.density).toInt(),
                        android.graphics.Color.DKGRAY
                    )
                }
                setOnClickListener {
                    dialog.dismiss()
                    changeHighlightColor(spanId, hex)
                }
            }
            colorRow.addView(circle)
        }
        layout.addView(colorRow)

        val btnDelete = com.google.android.material.button.MaterialButton(this).apply {
            text = "🗑 Remover marcação"
            setBackgroundColor(android.graphics.Color.parseColor("#DC143C"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                dialog.dismiss()
                deleteHighlight(spanId)
            }
        }
        layout.addView(btnDelete)

        dialog.setContentView(layout)
        dialog.show()
    }

    private fun changeHighlightColor(spanId: String, newColor: String) {
        binding.webView.evaluateJavascript("""
            (function() {
                var spans = document.querySelectorAll('[data-highlight-id="$spanId"]');
                spans.forEach(function(s) { s.style.backgroundColor = '$newColor'; });
            })()
        """.trimIndent(), null)
    }

    private fun deleteHighlight(spanId: String) {
        val js = """
            (function() {
                var spans = document.querySelectorAll('[data-highlight-id="$spanId"]');
                spans.forEach(function(span) {
                    var parent = span.parentNode;
                    while (span.firstChild) {
                        parent.insertBefore(span.firstChild, span);
                    }
                    parent.removeChild(span);
                });
                return '$spanId';
            })()
        """.trimIndent()

        binding.webView.evaluateJavascript(js) { result ->
            val id = result?.trim('"') ?: ""
            if (id.isNotBlank()) {
                android.util.Log.d("HIGHLIGHT", "Span removido do DOM: $id")
            }
        }
    }

    // ── Reaplicar highlights salvos ───────────────────────────────────────────

    private fun reapplyHighlights() {
        val bookId = intent.getLongExtra("BOOK_ID", -1)
        val chapterIndex = viewModel.currentChapterIndex.value ?: 0

        viewModel.getHighlightsForChapter(bookId, chapterIndex) { highlights ->
            if (highlights.isEmpty()) return@getHighlightsForChapter

            val js = buildString {
                append("(function() {")
                highlights.forEachIndexed { index, h ->
                    val escapedText = h.selectedText
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", " ")
                        .replace("\r", "")
                    val hex = h.color
                    val spanId = "hl_saved_$index"
                    append("""
                        (function() {
                            var spanId = '$spanId';
                            var text = '$escapedText';
                            var hex = '$hex';
                            var body = document.body;
                            var walker = document.createTreeWalker(
                                body, NodeFilter.SHOW_TEXT, null, false
                            );
                            var node;
                            while ((node = walker.nextNode())) {
                                var idx = node.nodeValue.indexOf(text);
                                if (idx >= 0) {
                                    var range = document.createRange();
                                    range.setStart(node, idx);
                                    range.setEnd(node, idx + text.length);
                                    var span = document.createElement('span');
                                    span.style.backgroundColor = hex;
                                    span.style.color = '#000000';
                                    span.dataset.highlightId = spanId;
                                    span.style.borderRadius = '2px';
                                    try { range.surroundContents(span); } catch(e) {}
                                    break;
                                }
                            }
                        })();
                    """.trimIndent())
                }
                append("})()")
            }

            runOnUiThread {
                binding.webView.evaluateJavascript(js) {
                    binding.webView.evaluateJavascript("""
                        window._readerListenersAttached = false;
                        $selectionJs
                    """.trimIndent(), null)
                }
            }
        }
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    private fun setupWebView() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isFullscreen) toggleFullscreen()
                return true
            }
        })

        // Intercepta o ActionMode nativo de seleção de texto para adicionar botões de highlight
        val highlightActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                highlightColors.forEachIndexed { i, _ ->
                    menu.add(Menu.NONE, 1000 + i, Menu.NONE, "${highlightEmojis[i]} Marcar")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val idx = item.itemId - 1000
                if (idx in highlightColors.indices) {
                    val hex = highlightColors[idx]
                    mode.finish()
                    applyHighlight(hex)
                    return true
                }
                return false
            }
            override fun onDestroyActionMode(mode: ActionMode) {}
        }

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = true
            addJavascriptInterface(FootnoteInterface(), "AndroidFootnote")
            addJavascriptInterface(SelectionInterface(), "AndroidSelection")

            // WebView herda de View; o callback de seleção fica em TextView,
            // mas podemos interceptar via startActionMode na própria Activity.
            // O highlightActionModeCallback é chamado em startHighlightActionMode()
            // acionado pelo selectionJs quando há texto selecionado.

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
                    view?.evaluateJavascript(selectionJs, null)
                    reapplyHighlights()

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

        binding.btnHighlight.setOnClickListener {
            val bookId = intent.getLongExtra("BOOK_ID", -1)
            startActivity(
                Intent(this, HighlightsActivity::class.java).putExtra("BOOK_ID", bookId)
            )
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