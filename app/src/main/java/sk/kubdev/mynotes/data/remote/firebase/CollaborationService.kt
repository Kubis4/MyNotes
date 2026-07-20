package sk.kubdev.mynotes.data.remote.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import sk.kubdev.mynotes.data.remote.models.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollaborationService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val notesCollection = firestore.collection("collaborative_notes")
    private val invitesCollection = firestore.collection("note_invites")
    private val usersCollection = firestore.collection("users")

    companion object {
        private const val TAG = "CollaborationService"
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid
    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    // Fetch the profiles (for photo URLs / display names) of the given user ids.
    // Missing/unreadable profiles are simply skipped, so a partial result never
    // fails the whole load. Returns a uid -> profile map.
    suspend fun getUserProfiles(userIds: List<String>): Map<String, UserProfile> {
        val result = mutableMapOf<String, UserProfile>()
        for (uid in userIds.distinct()) {
            try {
                val profile = usersCollection.document(uid).get().await()
                    .toObject(UserProfile::class.java)
                if (profile != null) result[uid] = profile.copy(userId = uid)
            } catch (e: Exception) {
                Log.w(TAG, "Could not load profile for $uid", e)
            }
        }
        return result
    }

    // Create collaborative note
    suspend fun createCollaborativeNote(
        title: String,
        content: String,
        type: String
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            Log.d(TAG, "=== Creating collaborative note ===")
            Log.d(TAG, "Title: $title")
            Log.d(TAG, "User: ${currentUser.email} (${currentUser.uid})")

            val note = CollaborativeNote(
                title = title,
                content = content,
                type = type,
                ownerId = currentUser.uid,
                collaborators = listOf(currentUser.uid),
                lastEditedBy = currentUser.uid,
                lastEditedByEmail = currentUser.email ?: "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val docRef = notesCollection.add(note).await()
            Log.d(TAG, "✅ Collaborative note created: ${docRef.id}")

            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating collaborative note", e)
            Result.failure(e)
        }
    }

    // Update collaborative note
    suspend fun updateCollaborativeNote(
        noteId: String,
        title: String,
        content: String
    ): Result<Unit> {
        return try {
            val userId = getCurrentUserId() ?: return Result.failure(Exception("User not authenticated"))
            val userEmail = getCurrentUserEmail() ?: ""

            val updates = mapOf(
                "title" to title,
                "content" to content,
                "updatedAt" to System.currentTimeMillis(),
                "lastEditedBy" to userId,
                "lastEditedByEmail" to userEmail
            )

            notesCollection.document(noteId).update(updates).await()
            Log.d(TAG, "Updated collaborative note: $noteId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating collaborative note", e)
            Result.failure(e)
        }
    }

    // Real-time note listener
    fun getCollaborativeNoteFlow(noteId: String): Flow<CollaborativeNote?> = callbackFlow {
        Log.d(TAG, "Starting real-time listener for note: $noteId")

        val listener = notesCollection.document(noteId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to note changes", error)
                    close(error)
                    return@addSnapshotListener
                }

                val note = snapshot?.toObject(CollaborativeNote::class.java)?.copy(id = snapshot.id)
                Log.d(TAG, "Note updated: $note")
                trySend(note)
            }

        awaitClose {
            Log.d(TAG, "Stopping real-time listener for note: $noteId")
            listener.remove()
        }
    }

    // Get user's collaborative notes
    fun getUserCollaborativeNotes(): Flow<List<CollaborativeNote>> = callbackFlow {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "No authenticated user")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        Log.d(TAG, "Starting listener for user notes: ${currentUser.uid}")

        val listener = notesCollection
            .whereArrayContains("collaborators", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to user notes", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val notes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val note = doc.toObject(CollaborativeNote::class.java)?.copy(id = doc.id)
                        Log.d(TAG, "Found collaborative note: ${note?.title} (${note?.id})")
                        note
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing note: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "User notes updated: ${notes.size} notes")
                notes.forEach { note ->
                    Log.d(TAG, "  - ${note.title} (collaborators: ${note.collaborators.size})")
                }

                trySend(notes)
            }

        awaitClose {
            Log.d(TAG, "Stopping listener for user notes")
            listener.remove()
        }
    }

    // ✅ FIXED: Send invitation using your field names
    suspend fun sendInvitation(
        noteId: String,
        email: String,
        role: CollaboratorRole = CollaboratorRole.EDITOR
    ): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))
            val userEmail = getCurrentUserEmail() ?: ""

            // Get note details for the invitation
            val noteDoc = notesCollection.document(noteId).get().await()
            val note = noteDoc.toObject(CollaborativeNote::class.java)
                ?: return Result.failure(Exception("Note not found"))

            if (note.ownerId != userId) {
                return Result.failure(Exception("Only the owner can send invitations"))
            }

            // Get user profile for display name
            val userProfile = usersCollection.document(userId).get().await()
                .toObject(UserProfile::class.java)

            // ✅ FIXED: Use your actual field names
            val invite = NoteInvite(
                noteId = noteId,
                noteTitle = note.title,
                senderEmail = userEmail,
                senderDisplayName = userProfile?.displayName ?: userEmail,
                recipientEmail = email,
                role = role,
                status = InviteStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000) // 7 days
            )

            invitesCollection.add(invite).await()
            Log.d(TAG, "Sent invitation to $email for note $noteId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending invitation", e)
            Result.failure(e)
        }
    }

    // ✅ FIXED: Get pending invitations using your field names
    fun getPendingInvitations(): Flow<List<NoteInvite>> = callbackFlow {
        val userEmail = getCurrentUserEmail()
        if (userEmail == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        Log.d(TAG, "Starting listener for pending invites: $userEmail")

        val listener = invitesCollection
            .whereEqualTo("recipientEmail", userEmail)
            .whereEqualTo("status", InviteStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to pending invites", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val invites = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(NoteInvite::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing invite: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "Pending invites updated: ${invites.size} invites")
                trySend(invites)
            }

        awaitClose {
            Log.d(TAG, "Stopping listener for pending invites")
            listener.remove()
        }
    }

    // ✅ FIXED: Accept invitation using your field names
    suspend fun acceptInvitation(inviteId: String): Result<Unit> {
        return try {
            Log.d(TAG, "=== Starting acceptInvitation ===")

            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "❌ User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            Log.d(TAG, "✅ Current user: ${currentUser.email} (${currentUser.uid})")

            // Get the invitation
            val inviteRef = invitesCollection.document(inviteId)
            val inviteSnapshot = inviteRef.get().await()

            if (!inviteSnapshot.exists()) {
                Log.e(TAG, "❌ Invitation not found")
                return Result.failure(Exception("Invitation not found"))
            }

            val invite = inviteSnapshot.toObject(NoteInvite::class.java)
            if (invite == null) {
                Log.e(TAG, "❌ Failed to parse invitation data")
                return Result.failure(Exception("Invalid invitation data"))
            }

            Log.d(TAG, "✅ Invitation found: ${invite.noteTitle}")

            // Validate invitation
            if (invite.status != InviteStatus.PENDING) {
                Log.e(TAG, "❌ Invitation is not pending: ${invite.status}")
                return Result.failure(Exception("Invitation is no longer pending"))
            }

            if (invite.recipientEmail != currentUser.email) {
                Log.e(TAG, "❌ Email mismatch: ${invite.recipientEmail} vs ${currentUser.email}")
                return Result.failure(Exception("Invitation is not for current user"))
            }

            // Check if invitation has expired
            if (System.currentTimeMillis() > invite.expiresAt) {
                Log.e(TAG, "❌ Invitation has expired")
                // Update invitation to expired status
                inviteRef.update("status", InviteStatus.EXPIRED.name).await()
                return Result.failure(Exception("Invitation has expired"))
            }

            Log.d(TAG, "✅ Validation passed, updating documents...")

            try {
                // Use a transaction to ensure consistency
                firestore.runTransaction { transaction ->
                    // Update invitation status
                    transaction.update(inviteRef, mapOf(
                        "status" to InviteStatus.ACCEPTED.name,
                        "acceptedAt" to System.currentTimeMillis()
                    ))

                    // Add user to collaborative note using arrayUnion
                    val noteRef = notesCollection.document(invite.noteId)
                    transaction.update(noteRef,
                        "collaborators",
                        com.google.firebase.firestore.FieldValue.arrayUnion(currentUser.uid)
                    )

                    Log.d(TAG, "✅ Transaction will add user ${currentUser.uid} to collaborators")
                }.await()
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                // The note was deleted after this invite was sent (the recipient can't
                // read the note doc pre-accept, so a dead invite only surfaces here as
                // NOT_FOUND / PERMISSION_DENIED). Expire the orphaned invite so it
                // disappears from the pending list instead of failing forever.
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND ||
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    Log.w(TAG, "Invite $inviteId points at a deleted note - expiring it")
                    try {
                        inviteRef.update("status", InviteStatus.EXPIRED.name).await()
                    } catch (expireError: Exception) {
                        Log.e(TAG, "Failed to expire orphaned invite", expireError)
                    }
                    return Result.failure(Exception("This note no longer exists"))
                }
                throw e
            }

            Log.d(TAG, "=== Successfully accepted invitation ===")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error accepting invitation: $inviteId", e)
            Result.failure(e)
        }
    }

    // Decline invitation
    suspend fun declineInvitation(inviteId: String): Result<Unit> {
        return try {
            invitesCollection.document(inviteId)
                .update("status", InviteStatus.DECLINED.name)
                .await()

            Log.d(TAG, "Invitation declined: $inviteId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error declining invitation", e)
            Result.failure(e)
        }
    }

    // Remove user from collaborative note
    suspend fun removeUserFromCollaborativeNote(collaborativeId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            Log.d(TAG, "=== Removing user from collaborative note ===")
            Log.d(TAG, "Note ID: $collaborativeId")
            Log.d(TAG, "User: ${currentUser.email} (${currentUser.uid})")

            val noteRef = notesCollection.document(collaborativeId)
            val noteSnapshot = noteRef.get().await()

            if (!noteSnapshot.exists()) {
                Log.e(TAG, "Collaborative note not found: $collaborativeId")
                return Result.failure(Exception("Collaborative note not found"))
            }

            val note = noteSnapshot.toObject(CollaborativeNote::class.java)
            if (note == null) {
                Log.e(TAG, "Failed to parse collaborative note")
                return Result.failure(Exception("Invalid note data"))
            }

            // Check if user is in collaborators list
            if (!note.collaborators.contains(currentUser.uid)) {
                Log.e(TAG, "User is not a collaborator of this note")
                return Result.failure(Exception("You are not a collaborator of this note"))
            }

            // If user is the owner and there are other collaborators, transfer ownership
            val updatedCollaborators = note.collaborators.filter { it != currentUser.uid }

            if (note.ownerId == currentUser.uid) {
                if (updatedCollaborators.isNotEmpty()) {
                    // Transfer ownership to the first remaining collaborator
                    val newOwnerId = updatedCollaborators.first()
                    noteRef.update(mapOf(
                        "collaborators" to updatedCollaborators,
                        "ownerId" to newOwnerId,
                        "updatedAt" to System.currentTimeMillis()
                    )).await()
                    Log.d(TAG, "✅ Ownership transferred to: $newOwnerId")
                } else {
                    // If no other collaborators, delete the note
                    noteRef.delete().await()
                    Log.d(TAG, "✅ Note deleted as no collaborators remain")
                }
            } else {
                // Just remove user from collaborators
                noteRef.update(mapOf(
                    "collaborators" to updatedCollaborators,
                    "updatedAt" to System.currentTimeMillis()
                )).await()
                Log.d(TAG, "✅ User removed from collaborators")
            }

            Log.d(TAG, "=== Successfully removed user from collaborative note ===")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error removing user from collaborative note: $collaborativeId", e)
            Result.failure(e)
        }
    }

    // Delete collaborative note completely
    suspend fun deleteCollaborativeNote(noteId: String): Result<Unit> {
        return try {
            val userId = getCurrentUserId() ?: return Result.failure(Exception("User not authenticated"))

            val noteRef = notesCollection.document(noteId)
            val noteSnapshot = noteRef.get().await()

            if (!noteSnapshot.exists()) {
                return Result.failure(Exception("Note not found"))
            }

            val note = noteSnapshot.toObject(CollaborativeNote::class.java)
            if (note?.ownerId != userId) {
                return Result.failure(Exception("Only the owner can delete the note"))
            }

            // Delete the note
            noteRef.delete().await()

            // Delete associated invitations
            val invites = invitesCollection.whereEqualTo("noteId", noteId).get().await()
            for (invite in invites) {
                invite.reference.delete()
            }

            Log.d(TAG, "Successfully deleted collaborative note: $noteId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting collaborative note", e)
            Result.failure(e)
        }
    }

    // Get collaborative note by ID
    suspend fun getCollaborativeNoteById(noteId: String): Result<CollaborativeNote?> {
        return try {
            val noteSnapshot = notesCollection.document(noteId).get().await()
            val note = noteSnapshot.toObject(CollaborativeNote::class.java)?.copy(id = noteSnapshot.id)
            Log.d(TAG, "Retrieved collaborative note: ${note?.title} (${note?.id})")
            Result.success(note)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting collaborative note: $noteId", e)
            Result.failure(e)
        }
    }

    // Update collaborative note title
    suspend fun updateCollaborativeNoteTitle(noteId: String, title: String): Result<Unit> {
        return try {
            val userId = getCurrentUserId() ?: return Result.failure(Exception("User not authenticated"))
            val userEmail = getCurrentUserEmail() ?: ""

            notesCollection.document(noteId).update(
                mapOf(
                    "title" to title,
                    "updatedAt" to System.currentTimeMillis(),
                    "lastEditedBy" to userId,
                    "lastEditedByEmail" to userEmail
                )
            ).await()
            Log.d(TAG, "Updated collaborative note title: $noteId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating collaborative note title", e)
            Result.failure(e)
        }
    }

    // Update collaborative note content
    suspend fun updateCollaborativeNoteContent(noteId: String, content: String): Result<Unit> {
        return try {
            val userId = getCurrentUserId() ?: return Result.failure(Exception("User not authenticated"))
            val userEmail = getCurrentUserEmail() ?: ""

            notesCollection.document(noteId).update(
                mapOf(
                    "content" to content,
                    "updatedAt" to System.currentTimeMillis(),
                    "lastEditedBy" to userId,
                    "lastEditedByEmail" to userEmail
                )
            ).await()
            Log.d(TAG, "Updated collaborative note content: $noteId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating collaborative note content", e)
            Result.failure(e)
        }
    }

    // Get collaborators for a note
    suspend fun getNoteCollaborators(noteId: String): Result<List<CollaboratorInfo>> {
        return try {
            val noteSnapshot = notesCollection.document(noteId).get().await()
            val note = noteSnapshot.toObject(CollaborativeNote::class.java)
                ?: return Result.failure(Exception("Note not found"))

            val collaborators = mutableListOf<CollaboratorInfo>()

            for (userId in note.collaborators) {
                val userSnapshot = usersCollection.document(userId).get().await()
                val userData = userSnapshot.data

                val collaboratorInfo = CollaboratorInfo(
                    userId = userId,
                    email = userData?.get("email") as? String ?: "unknown@email.com",
                    displayName = userData?.get("displayName") as? String ?: "Unknown User",
                    role = if (userId == note.ownerId) CollaboratorRole.OWNER else CollaboratorRole.EDITOR,
                    invitedAt = userData?.get("createdAt") as? Long ?: System.currentTimeMillis(),
                    status = InviteStatus.ACCEPTED
                )
                collaborators.add(collaboratorInfo)
            }

            Result.success(collaborators)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting note collaborators", e)
            Result.failure(e)
        }
    }
}
