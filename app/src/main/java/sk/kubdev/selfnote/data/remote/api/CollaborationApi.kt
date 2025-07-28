package sk.kubdev.selfnote.data.remote.api

import kotlinx.coroutines.flow.Flow
import sk.kubdev.selfnote.data.remote.models.*

/**
 * Clean API interface for collaboration features
 * This abstracts the Firebase implementation and could be swapped for other backends
 */
interface CollaborationApi {

    // ✅ Authentication
    fun isUserSignedIn(): Boolean
    fun getCurrentUserId(): String?
    fun getCurrentUserEmail(): String?
    fun getCurrentUserProfile(): Flow<UserProfile?>

    // ✅ Collaborative Notes CRUD
    suspend fun createCollaborativeNote(
        title: String,
        content: String,
        type: String = "CHECKLIST"
    ): Result<String>

    suspend fun getCollaborativeNote(noteId: String): Result<CollaborativeNote?>

    suspend fun updateCollaborativeNote(
        noteId: String,
        title: String,
        content: String
    ): Result<Unit>

    suspend fun updateCollaborativeNoteTitle(
        noteId: String,
        title: String
    ): Result<Unit>

    suspend fun updateCollaborativeNoteContent(
        noteId: String,
        content: String
    ): Result<Unit>

    suspend fun deleteCollaborativeNote(noteId: String): Result<Unit>

    // ✅ Real-time Flows
    fun getCollaborativeNoteFlow(noteId: String): Flow<CollaborativeNote?>
    fun getUserCollaborativeNotes(): Flow<List<CollaborativeNote>>
    fun getCollaborativeNotesCount(): Flow<Int>

    // ✅ Collaboration Management
    suspend fun sendInvitation(
        noteId: String,
        toEmail: String,
        role: CollaboratorRole = CollaboratorRole.EDITOR
    ): Result<Unit>

    suspend fun acceptInvitation(inviteId: String): Result<Unit>
    suspend fun declineInvitation(inviteId: String): Result<Unit>
    suspend fun cancelInvitation(inviteId: String): Result<Unit>

    fun getPendingInvitations(): Flow<List<NoteInvite>>
    fun getSentInvitations(): Flow<List<NoteInvite>>

    // ✅ Collaborator Management
    suspend fun getNoteCollaborators(noteId: String): Result<List<CollaboratorInfo>>
    suspend fun removeCollaborator(noteId: String, userId: String): Result<Unit>
    suspend fun updateCollaboratorRole(noteId: String, userId: String, role: CollaboratorRole): Result<Unit>
    suspend fun leaveCollaborativeNote(noteId: String): Result<Unit>

    // ✅ User Management
    suspend fun getUserProfile(userId: String): Result<UserProfile?>
    suspend fun updateUserProfile(displayName: String): Result<Unit>
    suspend fun searchUsersByEmail(email: String): Result<List<UserProfile>>

    // ✅ Real-time Collaboration Features
    suspend fun updateUserPresence(noteId: String, isActive: Boolean): Result<Unit>
    suspend fun updateUserCursor(noteId: String, cursor: UserCursor): Result<Unit>
    suspend fun updateTypingIndicator(noteId: String, indicator: TypingIndicator): Result<Unit>

    fun getUserPresence(noteId: String): Flow<Map<String, Boolean>>
    fun getUserCursors(noteId: String): Flow<List<UserCursor>>
    fun getTypingIndicators(noteId: String): Flow<List<TypingIndicator>>

    // ✅ Utilities
    suspend fun checkUserExists(email: String): Result<Boolean>
    suspend fun transferOwnership(noteId: String, newOwnerId: String): Result<Unit>
    suspend fun duplicateCollaborativeNote(noteId: String, newTitle: String): Result<String>
}
