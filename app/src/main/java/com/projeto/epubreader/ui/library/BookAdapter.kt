package com.projeto.epubreader.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.projeto.epubreader.data.db.BookEntity
import com.projeto.epubreader.databinding.ItemBookBinding
import java.io.File

class BookAdapter(
    private val onClick: (BookEntity) -> Unit,
    private val onDelete: (BookEntity) -> Unit
) : ListAdapter<BookEntity, BookAdapter.BookViewHolder>(DiffCallback()) {

    inner class BookViewHolder(
        private val binding: ItemBookBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(book: BookEntity) {
            binding.tvTitle.text = book.title
            binding.tvAuthor.text = book.author

            val file = book.coverPath?.let { File(it) }
            if (file != null && file.exists()) {
                Glide.with(binding.imgCover.context)
                    .load(file)
                    .into(binding.imgCover)
            } else {
                binding.imgCover.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            // Progresso
            if (book.totalChapters > 0) {
                val percent = ((book.currentChapterIndex.toFloat() / book.totalChapters) * 100).toInt()
                binding.progressBar.progress = percent
                binding.tvProgress.text = "$percent%"
            } else {
                binding.progressBar.progress = 0
                binding.tvProgress.text = "0%"
            }

            binding.root.setOnClickListener { onClick(book) }
            binding.btnDelete.setOnClickListener { onDelete(book) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<BookEntity>() {
        override fun areItemsTheSame(oldItem: BookEntity, newItem: BookEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BookEntity, newItem: BookEntity): Boolean {
            return oldItem == newItem
        }
    }
}