package com.kevin.ink.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Convierte tipos que SQLite no entiende directo (aquí, el enum Format <-> texto). */
class Converters {
    @TypeConverter
    fun fromFormat(value: Book.Format): String = value.name

    @TypeConverter
    fun toFormat(value: String): Book.Format = Book.Format.valueOf(value)
}

@Database(entities = [Book::class, DailyReading::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class InkDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        /** v1 -> v2: columna finishedAt en books + tabla reading_days. Conserva la biblioteca. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN finishedAt INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS reading_days " +
                        "(epochDay INTEGER NOT NULL PRIMARY KEY, minutes INTEGER NOT NULL)"
                )
            }
        }

        /** v2 -> v3: el seguimiento pasó (temporalmente) a páginas (reading_days vacía). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS reading_days")
                db.execSQL(
                    "CREATE TABLE reading_days " +
                        "(epochDay INTEGER NOT NULL PRIMARY KEY, pages INTEGER NOT NULL)"
                )
            }
        }

        /** v3 -> v4: la meta diaria vuelve a tiempo (reading_days en minutos) y books gana pageCount. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS reading_days")
                db.execSQL(
                    "CREATE TABLE reading_days " +
                        "(epochDay INTEGER NOT NULL PRIMARY KEY, minutes INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE books ADD COLUMN pageCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var INSTANCE: InkDatabase? = null

        fun get(context: Context): InkDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InkDatabase::class.java,
                    "ink.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                    .also { INSTANCE = it }
            }
    }
}
