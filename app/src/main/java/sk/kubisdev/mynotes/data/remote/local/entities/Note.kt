package sk.kubisdev.mynotes.data.remote.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NoteType {
    TEXT, CHECKLIST
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val type: NoteType = NoteType.TEXT,
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val colorIndex: Int = 0,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val categoryId: Int? = null,
    val orderIndex: Int = 0,
    val collaborativeNoteId: String? = null,
    // Index into NotePatterns - subtle procedural background pattern drawn over the
    // note's color tint on cards and in the editor. 0 = none.
    val patternIndex: Int = 0,
    // Epoch millis of a one-shot reminder for this note (Google Keep style). Null =
    // no reminder set. When set, an exact alarm is scheduled to post a notification
    // that opens this note. Kept in the entity (not a side table) so it round-trips
    // through the Drive/local backup automatically.
    val reminderAt: Long? = null,
)
