package sk.kubdev.selfnote.data.remote.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import sk.kubdev.selfnote.Converters
import sk.kubdev.selfnote.data.remote.local.dao.CategoryDao
import sk.kubdev.selfnote.data.remote.local.dao.NoteDao
import sk.kubdev.selfnote.data.remote.local.entities.Category
import sk.kubdev.selfnote.data.remote.local.entities.Note

// Add Category to entities and increment version
@Database(entities = [Note::class, Category::class], version = 12, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao // ADD THIS LINE

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    // This is essential for handling the schema change without a manual migration.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
