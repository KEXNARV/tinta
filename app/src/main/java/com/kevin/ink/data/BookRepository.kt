package com.kevin.ink.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** Punto único de acceso a los libros y al seguimiento de lectura. */
class BookRepository(private val dao: BookDao) {

    val books: Flow<List<Book>> = dao.observeAll()
    val continueBook: Flow<Book?> = dao.observeContinue()

    suspend fun add(book: Book) = dao.upsert(book)
    suspend fun remove(book: Book) = dao.delete(book)

    // --- Stats ---

    /** Libros terminados en el año en curso. */
    fun booksThisYear(): Flow<Int> {
        val startOfYear = LocalDate.now().withDayOfYear(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dao.observeFinishedSince(startOfYear)
    }

    /** Minutos leídos hoy. */
    fun todayMinutes(): Flow<Int?> = dao.observeMinutes(LocalDate.now().toEpochDay())

    /** Días de lectura de las últimas ~14 semanas (para racha y minutos del mes). */
    fun recentDays(): Flow<List<DailyReading>> =
        dao.observeDaysSince(LocalDate.now().toEpochDay() - 100)

    // --- Escritura ---

    /** Suma minutos al día indicado (crea la fila si no existía). */
    suspend fun addReadingMinutes(epochDay: Long, minutes: Int) {
        if (minutes <= 0) return
        dao.insertDay(DailyReading(epochDay, 0))
        dao.addMinutes(epochDay, minutes)
    }

    suspend fun updateProgress(bookId: String, progress: Float, ts: Long) =
        dao.updateProgress(bookId, progress.coerceIn(0f, 1f), ts)

    suspend fun markFinished(bookId: String, ts: Long) = dao.markFinished(bookId, ts)

    suspend fun setPageCount(bookId: String, pageCount: Int) = dao.setPageCount(bookId, pageCount)
}

/** Racha de días consecutivos con lectura, contando desde hoy (o ayer si hoy aún es 0). */
fun computeStreak(days: List<DailyReading>, today: Long): Int {
    val read = days.filter { it.minutes > 0 }.map { it.epochDay }.toHashSet()
    var day = if (today in read) today else today - 1
    var streak = 0
    while (day in read) {
        streak++
        day--
    }
    return streak
}

/** Minutos sumados en el mes en curso. */
fun monthMinutes(days: List<DailyReading>): Int {
    val firstOfMonth = LocalDate.now().withDayOfMonth(1).toEpochDay()
    return days.filter { it.epochDay >= firstOfMonth }.sumOf { it.minutes }
}
