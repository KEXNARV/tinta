package com.kevin.ink.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.ink.data.Book
import com.kevin.ink.data.BookRepository
import com.kevin.ink.data.InkDatabase
import com.kevin.ink.data.computeStreak
import com.kevin.ink.data.monthMinutes
import com.kevin.ink.ui.reader.ReadingPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DetailUiState(
    val book: Book? = null,
    val upNext: List<Book> = emptyList(),
    val streak: Int = 0,
    val monthMinutes: Int = 0,
    val booksThisYear: Int = 0,
    val yearlyGoal: Int = 40,
)

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository(InkDatabase.get(app).bookDao())
    private val prefs = ReadingPrefs(app)
    private val bookId = MutableStateFlow<String?>(null)

    fun load(id: String) { bookId.value = id }

    val state: StateFlow<DetailUiState> = combine(
        bookId,
        repo.books,
        repo.recentDays(),
        repo.booksThisYear(),
    ) { id, books, days, finished ->
        DetailUiState(
            book = books.find { it.id == id },
            upNext = books.filter { it.id != id && it.finishedAt == null }.take(10),
            streak = computeStreak(days, LocalDate.now().toEpochDay()),
            monthMinutes = monthMinutes(days),
            booksThisYear = finished,
            yearlyGoal = prefs.yearlyBookGoal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())
}
