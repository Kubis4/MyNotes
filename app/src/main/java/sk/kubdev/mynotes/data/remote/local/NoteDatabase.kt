package sk.kubdev.mynotes.data.remote.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import sk.kubdev.mynotes.Converters
import sk.kubdev.mynotes.data.remote.local.dao.CategoryDao
import sk.kubdev.mynotes.data.remote.local.dao.NoteDao
import sk.kubdev.mynotes.data.remote.local.entities.Category
import sk.kubdev.mynotes.data.remote.local.entities.Note

// Add Category to entities and increment version
@Database(entities = [Note::class, Category::class], version = 13, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao // ADD THIS LINE

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        // Adds Note.patternIndex. MUST be a real migration: this builder also has
        // fallbackToDestructiveMigration(), which would otherwise WIPE every note on
        // the version bump. An explicit path always wins over the destructive
        // fallback, so upgrades keep user data.
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN patternIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    .addMigrations(MIGRATION_12_13)
                    // This is essential for handling the schema change without a manual migration.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
