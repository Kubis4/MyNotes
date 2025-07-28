package sk.kubdev.selfnote.data.repository

import kotlinx.coroutines.flow.Flow
import sk.kubdev.selfnote.data.remote.api.CollaborationApi
import sk.kubdev.selfnote.data.remote.models.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollaborationRepository @Inject constructor(
    private val collaborationApi: CollaborationApi
) {

    // ✅ Authentication
    fun isUserSignedIn(): Boolean = collaborationApi.isUserSignedIn()
    fun getCurrentUserId(): String? = collaborationApi.getCurrentUserId()
    fun getCurrentUserEmail(): String? = collaborationApi.getCurrentUserEmail()

    // ✅ Collaborative Notes
    suspend fun createCollaborativeNote(
        title: String,
        content: String,
        type: String = "CHECKLIST"
    ): Result<String> = collaborationApi.createCollaborativeNote(title, content, type)

    suspend fun getCollaborativeNote(noteId: String): Result<CollaborativeNote?> =
        collaborationApi.getCollaborativeNote(noteId)

    suspend fun updateCollaborativeNote(
        noteId: String,
        title: String,
        content: String
    ): Result<Unit> = collaborationApi.updateCollaborativeNote(noteId, title, content)

    suspend fun updateCollaborativeNoteTitle(noteId: String, title: String): Result<Unit> =
        collaborationApi.updateCollaborativeNoteTitle(noteId, title)

    suspend fun updateCollaborativeNoteContent(noteId: String, content: String): Result<Unit> =
        collaborationApi.updateCollaborativeNoteContent(noteId, content)

    // ✅ Real-time flows
    fun getUserCollaborativeNotes(): Flow<List<CollaborativeNote>> =
        collaborationApi.getUserCollaborativeNotes()

    fun getCollaborativeNoteFlow(noteId: String): Flow<CollaborativeNote?> =
        collaborationApi.getCollaborativeNoteFlow(noteId)

    fun getCollaborativeNotesCount(): Flow<Int> =
        collaborationApi.getCollaborativeNotesCount()

    // ✅ Invitations
    suspend fun sendInvitation(noteId: String, email: String): Result<Unit> =
        collaborationApi.sendInvitation(noteId, email)

    suspend fun acceptInvitation(inviteId: String): Result<Unit> =
        collaborationApi.acceptInvitation(inviteId)

    suspend fun declineInvitation(inviteId: String): Result<Unit> =
        collaborationApi.declineInvitation(inviteId)

    fun getPendingInvitations(): Flow<List<NoteInvite>> =
        collaborationApi.getPendingInvitations()

    // ✅ Collaborator management
    suspend fun getNoteCollaborators(noteId: String): Result<List<CollaboratorInfo>> =
        collaborationApi.getNoteCollaborators(noteId)

    suspend fun leaveCollaborativeNote(noteId: String): Result<Unit> =
        collaborationApi.leaveCollaborativeNote(noteId)

    suspend fun removeCollaborator(noteId: String, userId: String): Result<Unit> =
        collaborationApi.removeCollaborator(noteId, userId)

    // ✅ User management
    suspend fun getUserProfile(userId: String): Result<UserProfile?> =
        collaborationApi.getUserProfile(userId)

    suspend fun updateUserProfile(displayName: String): Result<Unit> =
        collaborationApi.updateUserProfile(displayName)
}
