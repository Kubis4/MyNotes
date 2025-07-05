package sk.kubdev.selfnote

import androidx.room.Entity
import androidx.room.PrimaryKey

// --- NEW: Enum to differentiate between note types ---
enum class NoteType {
    TEXT, CHECKLIST
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String, // JSON string of NoteLine list
    val type: NoteType, // --- FIX: Added type field to the database entity
    val createdAt: Long,
    val lastModifiedAt: Long
    // --- FIX: Removed the unused 'subtext' field ---
)