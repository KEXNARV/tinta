package com.kevin.ink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minutos leídos en un día concreto (clave = día epoch, LocalDate.toEpochDay()).
 * Base para la racha, los minutos del mes y la meta diaria (de tiempo).
 */
@Entity(tableName = "reading_days")
data class DailyReading(
    @PrimaryKey val epochDay: Long,
    val minutes: Int,
)
