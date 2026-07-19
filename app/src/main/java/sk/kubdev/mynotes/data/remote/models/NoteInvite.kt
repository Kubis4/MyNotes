package sk.kubdev.mynotes.data.remote.models

data class NoteInvite(
    var id: String = "",
    var noteId: String = "",
    var noteTitle: String = "",
    var senderEmail: String = "",
    var senderDisplayName: String? = null,
    var recipientEmail: String = "",
    var status: InviteStatus = InviteStatus.PENDING,
    var createdAt: Long = System.currentTimeMillis(),
    var expiresAt: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000), // 7 days
    var acceptedAt: Long? = null,
    var role: CollaboratorRole = CollaboratorRole.EDITOR
) {
    // ✅ CRITICAL: No-argument constructor for Firestore
    constructor() : this(
        "", "", "", "", null, "",
        InviteStatus.PENDING, 0L, 0L, null, CollaboratorRole.EDITOR
    )
}
