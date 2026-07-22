package sk.kubisdev.mynotes.data.remote.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sk.kubisdev.mynotes.data.remote.local.entities.Note

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

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND deletedAt < :cutoffTime")
    suspend fun deleteExpiredNotes(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 1 AND deletedAt < :cutoffTime")
    suspend fun getExpiredNotesCount(cutoffTime: Long): Int

    @Query("SELECT * FROM notes WHERE isDeleted = 1 AND deletedAt < :cutoffTime")
    suspend fun getExpiredNotes(cutoffTime: Long): List<Note>

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getAllNotesForBackup(): List<Note>

    // Active notes that still carry a reminder - used to re-arm exact alarms after a
    // reboot (alarms don't survive a restart) or an app update.
    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND isDeleted = 0")
    suspend fun getNotesWithReminders(): List<Note>

    // Clears a note's reminder without touching lastModifiedAt - used from the reminder
    // BroadcastReceiver once the alarm has fired, so the note stops showing a pending
    // reminder and a reboot reschedule doesn't re-arm an already-delivered one.
    @Query("UPDATE notes SET reminderAt = NULL WHERE id = :id")
    suspend fun clearReminder(id: Int)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}