package com.projeto.epubreader.ui.reader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.projeto.epubreader.R
import com.projeto.epubreader.parser.EpubParser
import com.projeto.epubreader.data.db.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IndexActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bookId = intent.getLongExtra("BOOK_ID", -1)
        if (bookId == -1L) { finish(); return }

        // Layout programático
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "Índice"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@IndexActivity)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(toolbar)
        root.addView(recyclerView)
        setContentView(root)

        CoroutineScope(Dispatchers.IO).launch {
            val repository = BookRepository(applicationContext)
            val parser = EpubParser(applicationContext)
            val book = repository.getBook(bookId) ?: return@launch
            val epubBook = parser.parse(java.io.File(book.filePath))
            val chapters = epubBook.chapters

            withContext(Dispatchers.Main) {
                recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                        val tv = TextView(parent.context).apply {
                            setPadding(48, 32, 48, 32)
                            textSize = 16f
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            setBackgroundResource(android.R.attr.selectableItemBackground.let {
                                val ta = context.obtainStyledAttributes(intArrayOf(it))
                                val res = ta.getResourceId(0, 0)
                                ta.recycle()
                                res
                            })
                        }
                        return object : RecyclerView.ViewHolder(tv) {}
                    }

                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                        val tv = holder.itemView as TextView
                        tv.text = chapters[position].title
                        tv.setOnClickListener {
                            val result = Intent().apply {
                                putExtra("CHAPTER_INDEX", position)
                            }
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    }

                    override fun getItemCount() = chapters.size
                }
            }
        }
    }
}