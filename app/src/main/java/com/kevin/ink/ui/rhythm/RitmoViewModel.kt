package com.kevin.ink.ui.rhythm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.ink.data.Book
import com.kevin.ink.data.BookRepository
import com.kevin.ink.data.InkDatabase
import com.kevin.ink.data.computeStreak
import com.kevin.ink.ui.reader.ReadingPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class WeekDay(val label: String, val minutes: Int, val isToday: Boolean)

data class RitmoUiState(
    val todayMinutes: Int = 0,
    val goalMinutes: Int = 30,
    val streak: Int = 0,
    val continueBook: Book? = null,
    // Actividad estilo GitHub: minutos por día, orden semana-mayor empezando en lunes.
    // -1 = día futuro (hueco para mantener la alineación de la cuadrícula).
    val activity: List<Int> = emptyList(),
    val activityWeeks: Int = 0,
    val week: List<WeekDay> = emptyList(),
    val weekTotal: Int = 0,
)

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")

class RitmoViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository(InkDatabase.get(app).bookDao())
    private val prefs = ReadingPrefs(app)

    val state: StateFlow<RitmoUiState> = combine(
        repo.continueBook,
        repo.recentDays(),
        repo.todayMinutes(),
    ) { cont, days, today ->
        val map = days.associate { it.epochDay to it.minutes }
        val todayDate = LocalDate.now()
        val todayEpoch = todayDate.toEpochDay()

        // Cuadrícula tipo GitHub: 26 semanas, columnas = semanas (lunes a domingo).
        val weeks = 26
        val mondayThisWeek = todayEpoch - (todayDate.dayOfWeek.value - 1)
        val startMonday = mondayThisWeek - (weeks - 1) * 7
        val activity = ArrayList<Int>(weeks * 7)
        for (w in 0 until weeks) {
            for (d in 0 until 7) {
                val epoch = startMonday + w * 7 + d
                activity.add(if (epoch > todayEpoch) -1 else (map[epoch] ?: 0))
            }
        }

        val week = (0 until 7).map { i ->
            val epoch = todayEpoch - 6 + i
            val dow = LocalDate.ofEpochDay(epoch).dayOfWeek.value // 1=Lun..7=Dom
            WeekDay(DAY_LABELS[dow - 1], map[epoch] ?: 0, epoch == todayEpoch)
        }

        RitmoUiState(
            todayMinutes = today ?: 0,
            goalMinutes = prefs.dailyGoalMinutes,
            streak = computeStreak(days, todayEpoch),
            continueBook = cont,
            activity = activity,
            activityWeeks = weeks,
            week = week,
            weekTotal = week.sumOf { it.minutes },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RitmoUiState())
}
