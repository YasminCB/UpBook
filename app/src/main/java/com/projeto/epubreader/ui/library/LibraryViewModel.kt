package com.projeto.epubreader.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.epubreader.data.db.BookRepository
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    val books = repository.allBooks


    fun importBook(uri: Uri) = viewModelScope.launch {
        repository.importBook(uri)
    }

    fun deleteBook(bookId: Long) = viewModelScope.launch {
        repository.deleteBook(bookId)
    }
}