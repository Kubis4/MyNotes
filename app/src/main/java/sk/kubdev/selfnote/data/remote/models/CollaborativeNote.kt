package sk.kubdev.selfnote.data.remote.models

import sk.kubdev.selfnote.NoteType

// ✅ Your existing models are perfect - keep them as they are!
data class CollaborativeNote(
    var id: String = "",
    var title: String = "",
    var content: String = "",
    var type: String = "CHECKLIST",
    var ownerId: String = "",
    var collaborators: List<String> = emptyList(), // List of user IDs
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var lastEditedBy: String = "",
    var lastEditedByEmail: String = "",
    var isPublic: Boolean = false
) {
    constructor() : this("", "", "", "CHECKLIST", "", emptyList(), 0L, 0L, "", "", false)
}

data class CollaboratorInfo(
    var userId: String = "",
    var email: String = "",
    var displayName: String = "",
    var role: CollaboratorRole = CollaboratorRole.EDITOR,
    var invitedAt: Long = System.currentTimeMillis(),
    var status: InviteStatus = InviteStatus.ACCEPTED
) {
    constructor() : this("", "", "", CollaboratorRole.EDITOR, 0L, InviteStatus.ACCEPTED)
}

data class UserProfile(
    var userId: String = "",
    var email: String = "",
    var displayName: String = "",
    var createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", 0L)
}

// ✅ ADD THESE MISSING ENUMS
enum class CollaboratorRole {
    OWNER, EDITOR, VIEWER
}

enum class InviteStatus {
    PENDING, ACCEPTED, DECLINED, EXPIRED
}

// ✅ Your existing additional models
data class CollaborativeUser(
    var userId: String = "",
    var email: String = "",
    var displayName: String = "",
    var role: String = "EDITOR", // Store as string for Firestore compatibility
    var color: String = "#FF4CAF50", // Hex color for user identification
    var isOnline: Boolean = false,
    var lastSeen: Long = System.currentTimeMillis(),
    var joinedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "EDITOR", "#FF4CAF50", false, 0L, 0L)

    // Helper function to get role enum
    fun getRoleEnum(): CollaboratorRole {
        return when (role.uppercase()) {
            "OWNER" -> CollaboratorRole.OWNER
            "VIEWER" -> CollaboratorRole.VIEWER
            else -> CollaboratorRole.EDITOR
        }
    }
}

// ✅ NEW: Real-time collaboration cursor/selection tracking
data class UserCursor(
    var userId: String = "",
    var userEmail: String = "",
    var userDisplayName: String = "",
    var userColor: String = "#FF4CAF50",
    var lineId: String = "", // Which line they're editing
    var cursorPosition: Int = 0, // Cursor position in the line
    var selectionStart: Int = 0,
    var selectionEnd: Int = 0,
    var isActive: Boolean = true,
    var lastUpdated: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "#FF4CAF50", "", 0, 0, 0, true, 0L)
}

// ✅ NEW: Real-time typing indicators
data class TypingIndicator(
    var userId: String = "",
    var userEmail: String = "",
    var userDisplayName: String = "",
    var userColor: String = "#FF4CAF50",
    var lineId: String = "",
    var isTyping: Boolean = false,
    var lastTyped: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "#FF4CAF50", "", false, 0L)
}
