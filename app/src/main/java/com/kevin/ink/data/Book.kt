package com.kevin.ink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** La "ficha" de un libro guardada en la biblioteca. */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,        // identificador único (uuid)
    val title: String,
    val author: String?,
    val format: Format,                // EPUB o PDF
    val filePath: String,              // copia del archivo dentro de la app
    val coverPath: String?,            // portada extraída (puede no haber)
    val addedAt: Long,                 // cuándo se agregó (millis)
    val lastReadAt: Long? = null,      // para "seguir leyendo": última vez abierto
    val progress: Float = 0f,          // 0..1, por dónde va la lectura
    val finishedAt: Long? = null,      // cuándo se terminó (para "libros este año")
    val pageCount: Int = 0             // páginas (PDF real, EPUB estimado) para "p. X / Y"
) {
    enum class Format { EPUB, PDF }
}
