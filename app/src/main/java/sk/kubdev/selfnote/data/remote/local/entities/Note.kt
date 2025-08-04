package sk.kubdev.selfnote.data.remote.local.entities

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
)
