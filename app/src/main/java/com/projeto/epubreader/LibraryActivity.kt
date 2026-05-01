package com.projeto.epubreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.projeto.epubreader.databinding.ActivityLibraryBinding
import com.projeto.epubreader.ui.reader.ReaderActivity
import com.projeto.epubreader.ui.library.LibraryViewModel
import com.projeto.epubreader.ui.library.BookAdapter

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val viewModel: LibraryViewModel by viewModels()

    private val pickEpub = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importBook(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val adapter = BookAdapter { book ->
            // Abre o leitor
            val intent = Intent(this, ReaderActivity::class.java)
            intent.putExtra("BOOK_ID", book.id)
            startActivity(intent)
        }

        binding.recyclerView.adapter = adapter

        viewModel.books.observe(this) { books ->
            adapter.submitList(books)
        }

        binding.fabAdd.setOnClickListener {
            pickEpub.launch("application/epub+zip")
        }
    }
}