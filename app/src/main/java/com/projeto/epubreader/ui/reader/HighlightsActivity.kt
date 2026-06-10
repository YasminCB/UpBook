package com.projeto.epubreader.ui.reader

import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.projeto.epubreader.data.db.AppDatabase
import com.projeto.epubreader.data.db.HighlightEntity
import kotlinx.coroutines.launch

class HighlightsActivity : AppCompatActivity() {

    private val colors = listOf("yellow", "blue", "green", "pink")
    private val colorNames = listOf("Amarelo", "Azul", "Verde", "Rosa")
    private val colorHex = listOf("#FFD700", "#00BFFF", "#00FF7F", "#DC143C")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bookId = intent.getLongExtra("BOOK_ID", -1)
        if (bookId == -1L) { finish(); return }

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "Marcações"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }

        val tabLayout = TabLayout(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        colors.forEachIndexed { i, _ ->
            tabLayout.addTab(tabLayout.newTab().apply {
                text = colorNames[i]
                view?.setBackgroundColor(android.graphics.Color.parseColor(colorHex[i]))
            })
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HighlightsActivity)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        root.addView(toolbar)
        root.addView(tabLayout)
        root.addView(recyclerView)
        setContentView(root)

        val dao = AppDatabase.getInstance(this).highlightDao()

        fun loadColor(color: String) {
            lifecycleScope.launch {
                dao.getByBookAndColor(bookId, color).collect { highlights ->
                    recyclerView.adapter = HighlightAdapter(highlights, colorHex[colors.indexOf(color)]) { highlight ->
                        lifecycleScope.launch { dao.delete(highlight) }
                    }
                }
            }
        }

        loadColor(colors[0])

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { loadColor(colors[tab.position]) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }
}

class HighlightAdapter(
    private val items: List<HighlightEntity>,
    private val colorHex: String,
    private val onDelete: (HighlightEntity) -> Unit
) : RecyclerView.Adapter<HighlightAdapter.VH>() {

    inner class VH(val layout: android.widget.LinearLayout) : RecyclerView.ViewHolder(layout)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        return VH(layout)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.layout.removeAllViews()

        val bar = android.view.View(holder.layout.context).apply {
            setBackgroundColor(android.graphics.Color.parseColor(colorHex))
            layoutParams = android.widget.LinearLayout.LayoutParams(8,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT).also {
                it.marginEnd = 12
            }
        }

        val tv = TextView(holder.layout.context).apply {
            text = item.selectedText
            textSize = 14f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnDel = android.widget.ImageButton(holder.layout.context).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            background = null
            setOnClickListener { onDelete(item) }
        }

        holder.layout.addView(bar)
        holder.layout.addView(tv)
        holder.layout.addView(btnDel)
    }

    override fun getItemCount() = items.size
}