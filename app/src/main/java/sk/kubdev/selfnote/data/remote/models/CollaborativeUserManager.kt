package sk.kubdev.selfnote.data.remote.models

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class CollaborativeUserManager {
    private val _activeUsers = MutableStateFlow<Map<String, CollaborativeUser>>(emptyMap())
    val activeUsers: Flow<Map<String, CollaborativeUser>> = _activeUsers.asStateFlow()

    private val _userCursors = MutableStateFlow<Map<String, UserCursor>>(emptyMap())
    val userCursors: Flow<Map<String, UserCursor>> = _userCursors.asStateFlow()

    private val _typingIndicators = MutableStateFlow<Map<String, TypingIndicator>>(emptyMap())
    val typingIndicators: Flow<Map<String, TypingIndicator>> = _typingIndicators.asStateFlow()

    private val userCache = ConcurrentHashMap<String, CollaborativeUser>()

    companion object {
        private const val TAG = "CollaborativeUserManager"
        private const val USER_TIMEOUT_MS = 30_000L // 30 seconds
        private const val TYPING_TIMEOUT_MS = 3_000L // 3 seconds
    }

    fun addUser(user: CollaborativeUser) {
        Log.d(TAG, "Adding user: ${user.email}")
        userCache[user.userId] = user
        updateActiveUsers()
    }

    fun removeUser(userId: String) {
        Log.d(TAG, "Removing user: $userId")
        userCache.remove(userId)
        updateActiveUsers()
    }

    fun updateUserStatus(userId: String, isOnline: Boolean) {
        userCache[userId]?.let { user ->
            userCache[userId] = user.copy(
                isOnline = isOnline,
                lastSeen = if (isOnline) System.currentTimeMillis() else user.lastSeen
            )
            updateActiveUsers()
        }
    }

    fun updateUserCursor(cursor: UserCursor) {
        val cursors = _userCursors.value.toMutableMap()
        cursors[cursor.userId] = cursor
        _userCursors.value = cursors

        // Auto-remove old cursors
        cleanupOldCursors()
    }

    fun updateTypingIndicator(indicator: TypingIndicator) {
        val indicators = _typingIndicators.value.toMutableMap()
        if (indicator.isTyping) {
            indicators[indicator.userId] = indicator
        } else {
            indicators.remove(indicator.userId)
        }
        _typingIndicators.value = indicators

        // Auto-remove old typing indicators
        cleanupOldTypingIndicators()
    }

    fun getUserById(userId: String): CollaborativeUser? {
        return userCache[userId]
    }

    fun getUserByEmail(email: String): CollaborativeUser? {
        return userCache.values.find { it.email == email }
    }

    fun getActiveUserCount(): Int {
        return userCache.values.count { it.isOnline }
    }

    private fun updateActiveUsers() {
        _activeUsers.value = userCache.toMap()
    }

    private fun cleanupOldCursors() {
        val now = System.currentTimeMillis()
        val cursors = _userCursors.value.toMutableMap()

        cursors.entries.removeAll { (_, cursor) ->
            now - cursor.lastUpdated > USER_TIMEOUT_MS
        }

        _userCursors.value = cursors
    }

    private fun cleanupOldTypingIndicators() {
        val now = System.currentTimeMillis()
        val indicators = _typingIndicators.value.toMutableMap()

        indicators.entries.removeAll { (_, indicator) ->
            now - indicator.lastTyped > TYPING_TIMEOUT_MS
        }

        _typingIndicators.value = indicators
    }

    fun clear() {
        userCache.clear()
        _activeUsers.value = emptyMap()
        _userCursors.value = emptyMap()
        _typingIndicators.value = emptyMap()
    }
}
