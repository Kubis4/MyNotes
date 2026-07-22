package sk.kubisdev.mynotes.data.remote.models

import sk.kubisdev.mynotes.NoteLine
import sk.kubisdev.mynotes.data.remote.local.entities.NoteType
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

// ✅ Your existing extensions
fun String.toNoteLines(): List<NoteLine> {
    return try {
        if (this.trim().startsWith("[") && this.trim().endsWith("]")) {
            Json.decodeFromString<List<NoteLine>>(this)
        } else {
            this.lines().map { line ->
                NoteLine.create(content = line)
            }.takeIf { it.isNotEmpty() } ?: listOf(NoteLine.create())
        }
    } catch (e: Exception) {
        this.lines().map { line ->
            NoteLine.create(content = line)
        }.takeIf { it.isNotEmpty() } ?: listOf(NoteLine.create())
    }
}

fun List<NoteLine>.toJson(): String {
    return try {
        Json.encodeToString(this)
    } catch (e: Exception) {
        "[]"
    }
}

// ✅ NEW: Collaborative extensions
fun CollaborativeNote.getNoteType(): NoteType {
    return when (type.uppercase()) {
        "TEXT" -> NoteType.TEXT
        "CHECKLIST" -> NoteType.CHECKLIST
        else -> NoteType.CHECKLIST
    }
}

fun NoteType.toCollaborativeString(): String {
    return when (this) {
        NoteType.TEXT -> "TEXT"
        NoteType.CHECKLIST -> "CHECKLIST"
    }
}

// ✅ NEW: Generate random colors for collaborators
fun generateUserColor(): String {
    val colors = listOf(
        "#FF4CAF50", // Green
        "#FF2196F3", // Blue
        "#FFFF9800", // Orange
        "#FF9C27B0", // Purple
        "#FFF44336", // Red
        "#FF00BCD4", // Cyan
        "#FFFF5722", // Deep Orange
        "#FF3F51B5", // Indigo
        "#FF8BC34A", // Light Green
        "#FFCDDC39", // Lime
    )
    return colors[Random.nextInt(colors.size)]
}

// ✅ NEW: Convert hex color to Compose Color
fun String.toComposeColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color(0xFF4CAF50) // Default green
    }
}

// ✅ NEW: Get user initials for avatars
fun String.getInitials(): String {
    return this.split(" ", "@", ".")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
}

// ✅ NEW: Check if user is owner
fun CollaborativeNote.isUserOwner(userId: String): Boolean {
    return this.ownerId == userId
}

// ✅ NEW: Check if user can edit
fun CollaborativeNote.canUserEdit(userId: String): Boolean {
    return this.ownerId == userId || this.collaborators.contains(userId)
}

// ✅ NEW: Get collaborator count
fun CollaborativeNote.getCollaboratorCount(): Int {
    return this.collaborators.size
}

// ✅ NEW: Format last edited time
fun CollaborativeNote.getLastEditedTimeString(): String {
    val now = System.currentTimeMillis()
    val diff = now - this.updatedAt

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}

// ✅ NEW: Create CollaborativeUser from UserProfile
fun UserProfile.toCollaborativeUser(role: CollaboratorRole = CollaboratorRole.EDITOR): CollaborativeUser {
    return CollaborativeUser(
        userId = this.userId,
        email = this.email,
        displayName = this.displayName,
        role = role.name,
        color = generateUserColor(),
        isOnline = true,
        lastSeen = System.currentTimeMillis(),
        joinedAt = System.currentTimeMillis()
    )
}

// ✅ NEW: Check if invite is expired
fun NoteInvite.isExpired(): Boolean {
    val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
    return System.currentTimeMillis() - this.createdAt > sevenDaysInMillis
}

// ✅ NEW: Get invite status display text
fun NoteInvite.getStatusText(): String {
    return when {
        isExpired() -> "Expired"
        status == InviteStatus.PENDING -> "Pending"
        status == InviteStatus.ACCEPTED -> "Accepted"
        status == InviteStatus.DECLINED -> "Declined"
        else -> "Unknown"
    }
}
