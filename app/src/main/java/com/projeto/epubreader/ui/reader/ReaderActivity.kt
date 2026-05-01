package com.projeto.epubreader.ui.reader

import android.os.Bundle
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.projeto.epubreader.databinding.ActivityReaderBinding

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModels()
    private var totalChapters = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val bookId = intent.getLongExtra("BOOK_ID", -1)
        if (bookId == -1L) { finish(); return }

        setupWebView()
        setupButtons()
        viewModel.loadBook(bookId)
        observeViewModel()
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = WebViewClient()
        }
    }

    private fun setupButtons() {
        binding.btnPrevChapter.setOnClickListener {
            val current = viewModel.currentChapterIndex.value ?: 0
            if (current > 0) viewModel.loadChapter(current - 1)
        }

        binding.btnNextChapter.setOnClickListener {
            val current = viewModel.currentChapterIndex.value ?: 0
            if (current < totalChapters - 1) viewModel.loadChapter(current + 1)
        }
    }

    private fun observeViewModel() {
        viewModel.currentBook.observe(this) { book ->
            supportActionBar?.title = book.title
        }

        viewModel.currentChapterIndex.observe(this) { index ->
            binding.tvChapterInfo.text = "Cap. ${index + 1} / $totalChapters"
        }

        viewModel.chapterContent.observe(this) { html ->
            val book = viewModel.currentBook.value ?: return@observe
            // Usa file:// para que o WebView resolva imagens/CSS relativos
            binding.webView.loadDataWithBaseURL(
                "file://${book.extractedDir}/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Salva progresso ao sair
        val scrollY = binding.webView.scrollY
        viewModel.saveProgress(scrollY)
    }
}