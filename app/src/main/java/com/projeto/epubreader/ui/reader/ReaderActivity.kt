package com.projeto.epubreader.ui.reader

import android.os.Bundle
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.projeto.epubreader.databinding.ActivityReaderBinding

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModels()

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
        viewModel.loadBook(bookId)
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.allowFileAccess = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true          // ← adicione essa
            settings.allowFileAccessFromFileURLs = true // ← e essa
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
            val total = viewModel.totalChapters.value ?: 0
            if (current < total - 1) viewModel.loadChapter(current + 1)
        }
    }

    private fun observeViewModel() {
        // Título do livro
        viewModel.currentBook.observe(this) { book ->
            supportActionBar?.title = book.title
        }

        // Atualiza info "Cap. X / Y" sempre que capítulo ou total mudar
        viewModel.currentChapterIndex.observe(this) { index ->
            val total = viewModel.totalChapters.value ?: 0
            binding.tvChapterInfo.text = "Cap. ${index + 1} / $total"
        }

        viewModel.totalChapters.observe(this) { total ->
            val index = viewModel.currentChapterIndex.value ?: 0
            binding.tvChapterInfo.text = "Cap. ${index + 1} / $total"
        }

        // Carrega HTML no WebView
        viewModel.chapterContent.observe(this) { html ->
            val baseDir = viewModel.chapterBaseDir.value ?: return@observe
            binding.webView.loadDataWithBaseURL(
                "file://$baseDir/",   // ← pasta real do capítulo
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveProgress(binding.webView.scrollY)
    }
}