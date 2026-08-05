package com.kevin.ink.ui.reader

import android.content.Context

/**
 * Ajustes de lectura (globales) y posición por libro, guardados de forma simple.
 * No usa la base de datos para no complicar migraciones.
 */
class ReadingPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("ink_reading", Context.MODE_PRIVATE)

    // --- Ajustes globales ---
    var fontZoom: Int
        get() = prefs.getInt("font_zoom", 110)
        set(value) = prefs.edit().putInt("font_zoom", value).apply()

    var theme: String
        get() = prefs.getString("theme", "night") ?: "night"
        set(value) = prefs.edit().putString("theme", value).apply()

    var pdfZoom: Int
        get() = prefs.getInt("pdf_zoom", 100)
        set(value) = prefs.edit().putInt("pdf_zoom", value).apply()

    // --- Metas (pantalla de inicio) ---
    var dailyGoalMinutes: Int
        get() = prefs.getInt("daily_goal_min", 30)
        set(value) = prefs.edit().putInt("daily_goal_min", value).apply()

    var yearlyBookGoal: Int
        get() = prefs.getInt("yearly_book_goal", 40)
        set(value) = prefs.edit().putInt("yearly_book_goal", value).apply()

    // Tema de la app (true = oscuro "ink", false = claro). Por defecto oscuro.
    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    // --- Posición por libro ---
    fun chapterOf(bookId: String): Int = prefs.getInt("ch_$bookId", 0)

    fun scrollOf(bookId: String): Float = prefs.getFloat("sc_$bookId", 0f)

    fun saveChapter(bookId: String, index: Int) {
        prefs.edit().putInt("ch_$bookId", index).apply()
    }

    fun saveScroll(bookId: String, fraction: Float) {
        prefs.edit().putFloat("sc_$bookId", fraction).apply()
    }

    // --- Posición en PDF (número de página) ---
    fun pageOf(bookId: String): Int = prefs.getInt("pg_$bookId", 0)

    fun savePage(bookId: String, index: Int) {
        prefs.edit().putInt("pg_$bookId", index).apply()
    }

    // --- Modo líquido por libro (PDFs) ---
    fun liquidOf(bookId: String): Boolean = prefs.getBoolean("lq_$bookId", false)

    fun saveLiquid(bookId: String, on: Boolean) {
        prefs.edit().putBoolean("lq_$bookId", on).apply()
    }
}
