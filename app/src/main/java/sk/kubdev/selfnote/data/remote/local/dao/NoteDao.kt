package sk.kubdev.selfnote.data.remote.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sk.kubdev.selfnote.data.remote.local.entities.Note

@Dao
interface NoteDao {

    // --- FIX: Add a 'Long' return type here ---
    // This tells Room to return the ID of the inserted or updated note.
    @Upsert
    suspend fun insertOrUpdate(note: Note): Long

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM notes ORDER BY lastModifiedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Int): Flow<Note?>

    @Query("SELECT * FROM notes WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(content) LIKE '%' || LOWER(:query) || '%' ORDER BY lastModifiedAt DESC")
    fun searchNotes(query: String): Flow<List<Note>>

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND deletedAt < :cutoffTime")
    suspend fun deleteExpiredNotes(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 1 AND deletedAt < :cutoffTime")
    suspend fun getExpiredNotesCount(cutoffTime: Long): Int

    @Query("SELECT * FROM notes WHERE isDeleted = 1 AND deletedAt < :cutoffTime")
    suspend fun getExpiredNotes(cutoffTime: Long): List<Note>

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getAllNotesForBackup(): List<Note>
}