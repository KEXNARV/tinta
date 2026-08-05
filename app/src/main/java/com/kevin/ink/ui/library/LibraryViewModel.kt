package com.kevin.ink.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.ink.data.Book
import com.kevin.ink.data.BookRepository
import com.kevin.ink.data.InkDatabase
import com.kevin.ink.importer.BookImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository(InkDatabase.get(app).bookDao())
    private val importer = BookImporter(app)

    val books: StateFlow<List<Book>> = repo.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val book = importer.import(uri)
                if (book != null) {
                    repo.add(book)
                    _message.value = "Agregado: ${book.title}"
                } else {
                    _message.value = "No pude leer ese archivo (¿es EPUB o PDF?)"
                }
            } catch (e: Exception) {
                _message.value = "Error al importar: ${e.message}"
            } finally {
                _importing.value = false
            }
        }
    }

    fun delete(book: Book) {
        viewModelScope.launch {
            repo.remove(book)
            runCatching {
                File(book.filePath).delete()
                book.coverPath?.let { File(it).delete() }
            }
            _message.value = "Eliminado: ${book.title}"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
