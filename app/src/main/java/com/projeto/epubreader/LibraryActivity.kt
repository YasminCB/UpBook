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
import com.projeto.epubreader.ui.theme.ThemeManager

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val viewModel: LibraryViewModel by viewModels()

    private val pickEpub = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importBook(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.getThemeRes(this))
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val isDark = ThemeManager.isDark(this)

        val switchLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = androidx.appcompat.widget.Toolbar.LayoutParams(
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.END
            )
        }

        val icon = android.widget.ImageView(this).apply {
            setImageResource(if (isDark) R.drawable.ic_moon else R.drawable.ic_sun)
            layoutParams = android.widget.LinearLayout.LayoutParams(40, 40).also {
                it.marginEnd = 8
            }
        }

        val switchTheme = android.widget.Switch(this).apply {
            isChecked = isDark
            thumbTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#6B47CB")
            )
            setOnCheckedChangeListener { _, dark ->
                icon.setImageResource(if (dark) R.drawable.ic_moon else R.drawable.ic_sun)
                ThemeManager.setDark(this@LibraryActivity, dark)
                recreate()
            }
        }

        switchLayout.addView(icon)
        switchLayout.addView(switchTheme)
        binding.toolbar.addView(switchLayout)

        val adapter = BookAdapter(
            onClick = { book ->
                val intent = Intent(this, ReaderActivity::class.java)
                intent.putExtra("BOOK_ID", book.id)
                startActivity(intent)
            },
            onDelete = { book ->
                android.app.AlertDialog.Builder(this)
                    .setTitle("Deletar livro")
                    .setMessage("Deseja remover \"${book.title}\"?")
                    .setPositiveButton("Deletar") { _, _ ->
                        viewModel.deleteBook(book.id)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )

        binding.recyclerView.adapter = adapter

        viewModel.books.observe(this) { books ->
            adapter.submitList(books)
        }

        binding.fabAdd.setOnClickListener {
            pickEpub.launch("application/epub+zip")
        }
    }
}