package com.kevin.ink.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Las operaciones disponibles sobre los libros y el seguimiento de lectura. */
@Dao
interface BookDao {
    // --- Libros ---
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: Book)

    @Delete
    suspend fun delete(book: Book)

    /** El libro para "seguir leyendo": el último abierto que aún no se ha terminado. */
    @Query("SELECT * FROM books WHERE lastReadAt IS NOT NULL AND finishedAt IS NULL ORDER BY lastReadAt DESC LIMIT 1")
    fun observeContinue(): Flow<Book?>

    /** Cuántos libros se terminaron desde un instante (p. ej. inicio del año). */
    @Query("SELECT COUNT(*) FROM books WHERE finishedAt IS NOT NULL AND finishedAt >= :since")
    fun observeFinishedSince(since: Long): Flow<Int>

    @Query("UPDATE books SET progress = :progress, lastReadAt = :ts WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, ts: Long)

    @Query("UPDATE books SET finishedAt = :ts WHERE id = :id AND finishedAt IS NULL")
    suspend fun markFinished(id: String, ts: Long)

    @Query("UPDATE books SET pageCount = :pageCount WHERE id = :id")
    suspend fun setPageCount(id: String, pageCount: Int)

    // --- Seguimiento por día (minutos leídos) ---
    @Query("SELECT minutes FROM reading_days WHERE epochDay = :day")
    fun observeMinutes(day: Long): Flow<Int?>

    @Query("SELECT * FROM reading_days WHERE epochDay >= :fromDay ORDER BY epochDay DESC")
    fun observeDaysSince(fromDay: Long): Flow<List<DailyReading>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDay(day: DailyReading)

    @Query("UPDATE reading_days SET minutes = minutes + :delta WHERE epochDay = :day")
    suspend fun addMinutes(day: Long, delta: Int)
}
