package com.kevin.ink.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.ink.data.Book
import com.kevin.ink.data.BookRepository
import com.kevin.ink.data.InkDatabase
import com.kevin.ink.data.computeStreak
import com.kevin.ink.data.monthMinutes
import com.kevin.ink.ui.reader.ReadingPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val continueBook: Book? = null,
    val library: List<Book> = emptyList(),
    val streakDays: Int = 0,
    val monthMinutes: Int = 0,
    val todayMinutes: Int = 0,
    val dailyGoalMinutes: Int = 30,
    val booksThisYear: Int = 0,
    val yearlyGoal: Int = 40,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository(InkDatabase.get(app).bookDao())
    private val prefs = ReadingPrefs(app)

    val state: StateFlow<HomeUiState> = combine(
        repo.books,
        repo.continueBook,
        repo.recentDays(),
        repo.todayMinutes(),
        repo.booksThisYear(),
    ) { books, cont, days, today, finished ->
        HomeUiState(
            loading = false,
            continueBook = cont ?: books.firstOrNull(),
            library = books,
            streakDays = computeStreak(days, LocalDate.now().toEpochDay()),
            monthMinutes = monthMinutes(days),
            todayMinutes = today ?: 0,
            dailyGoalMinutes = prefs.dailyGoalMinutes,
            booksThisYear = finished,
            yearlyGoal = prefs.yearlyBookGoal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
