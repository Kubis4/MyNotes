package sk.kubdev.selfnote

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sk.kubdev.selfnote.data.remote.firebase.AuthService
import sk.kubdev.selfnote.data.remote.firebase.CollaborationService
import sk.kubdev.selfnote.data.remote.local.NoteDatabase
import sk.kubdev.selfnote.data.remote.local.entities.Category
import sk.kubdev.selfnote.data.remote.local.entities.Note
import sk.kubdev.selfnote.data.remote.local.entities.NoteType
import sk.kubdev.selfnote.settings.model.SwipeAction
import sk.kubdev.selfnote.data.remote.models.CollaborativeNote
import sk.kubdev.selfnote.data.remote.models.NoteInvite
import sk.kubdev.selfnote.NoteLine
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.ui.theme.NoteColorPalette
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Data class for section context
data class SectionContext(
    val isPinned: Boolean,
    val categoryId: Int?,
    val isArchived: Boolean
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    application: Application,
    private val authService: AuthService,
    private val collaborationService: CollaborationService
) : AndroidViewModel(application) {

    private val noteDao = NoteDatabase.getDatabase(application).noteDao()
    private val categoryDao = NoteDatabase.getDatabase(application).categoryDao()
    private var autoSaveJob: Job? = null
    private val autoSaveDelay = 1500L // 1.5 seconds

    // AUTO-CLEANUP CONSTANTS
    companion object {
        private val BIN_RETENTION_DAYS = 30L
        private val BIN_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(BIN_RETENTION_DAYS)
        private val CLEANUP_CHECK_INTERVAL = TimeUnit.HOURS.toMillis(6) // Check every 6 hours
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    // Store recently deleted/archived notes for undo functionality
    private var recentlyDeletedNote: Note? = null
    private var recentlyArchivedNote: Note? = null

    // AUTO-CLEANUP JOB
    private var cleanupJob: Job? = null

    // COLLABORATIVE FEATURES
    private val _collaborativeNotes = MutableStateFlow<List<CollaborativeNote>>(emptyList())
    val collaborativeNotes: StateFlow<List<CollaborativeNote>> = _collaborativeNotes.asStateFlow()

    private val _pendingInvites = MutableStateFlow<List<NoteInvite>>(emptyList())
    val pendingInvites: StateFlow<List<NoteInvite>> = _pendingInvites.asStateFlow()

    private val _collaborationError = MutableStateFlow<String?>(null)
    val collaborationError: StateFlow<String?> = _collaborationError.asStateFlow()

    init {
        // START AUTO-CLEANUP ON INIT
        startAutoCleanup()
    }

    // Get all active notes (filtering archived and deleted ones)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allNotes: StateFlow<List<Note>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                noteDao.getAllNotes().map { notes ->
                    notes.filter { !it.isArchived && !it.isDeleted }
                }
            } else {
                noteDao.searchNotes("%$query%").map { notes ->
                    notes.filter { !it.isArchived && !it.isDeleted }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Get archived notes (for archive screen)
    val archivedNotes: StateFlow<List<Note>> = noteDao.getAllNotes()
        .map { notes -> notes.filter { it.isArchived && !it.isDeleted } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Get deleted notes (for bin screen) - ENHANCED WITH EXPIRY INFO
    val deletedNotes: StateFlow<List<Note>> = noteDao.getAllNotes()
        .map { notes ->
            notes.filter { it.isDeleted }
                .sortedByDescending { it.deletedAt ?: 0L } // Most recently deleted first
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val allCategories: StateFlow<List<Category>> = categoryDao.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // ✅ COLLABORATIVE FUNCTIONS
    fun startCollaborativeSync() {
        viewModelScope.launch {
            try {
                collaborationService.getUserCollaborativeNotes()
                    .collect { notes ->
                        _collaborativeNotes.value = notes
                    }
            } catch (e: Exception) {
                _collaborationError.value = "Failed to sync collaborative notes: ${e.message}"
            }
        }

        viewModelScope.launch {
            try {
                collaborationService.getPendingInvitations()
                    .collect { invites ->
                        _pendingInvites.value = invites
                    }
            } catch (e: Exception) {
                _collaborationError.value = "Failed to sync invitations: ${e.message}"
            }
        }
    }

    // ✅ Create collaborative note
    fun createCollaborativeNote(
        title: String,
        lines: List<NoteLine>,
        noteType: NoteType,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val content = lines.toJson()
                val result = collaborationService.createCollaborativeNote(title, content, noteType.name)
                result.onSuccess { noteId ->
                    onSuccess(noteId)
                }.onFailure { error ->
                    _collaborationError.value = "Failed to create collaborative note: ${error.message}"
                }
            } catch (e: Exception) {
                _collaborationError.value = "Failed to create collaborative note: ${e.message}"
            }
        }
    }

    // ✅ Update collaborative note
    fun updateCollaborativeNote(
        noteId: String,
        title: String,
        lines: List<NoteLine>
    ) {
        viewModelScope.launch {
            try {
                val content = lines.toJson()
                val result = collaborationService.updateCollaborativeNote(noteId, title, content)
                result.onFailure { error ->
                    _collaborationError.value = "Failed to update note: ${error.message}"
                }
            } catch (e: Exception) {
                _collaborationError.value = "Failed to update note: ${e.message}"
            }
        }
    }

    // ✅ Send invitation
    fun sendCollaborationInvite(
        noteId: String,
        email: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val result = collaborationService.sendInvitation(noteId, email)
            onResult(result.isSuccess)
        }
    }

    fun acceptInvitation(inviteId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = collaborationService.acceptInvitation(inviteId)
            onResult(result.isSuccess)
            if (result.isFailure) {
                _collaborationError.value = "Failed to accept invitation: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun declineInvitation(inviteId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = collaborationService.declineInvitation(inviteId)
            onResult(result.isSuccess)
            if (result.isFailure) {
                _collaborationError.value = "Failed to decline invitation: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // ✅ FIXED: Update signInWithGoogle callback signature
    fun signInWithGoogle(context: Context, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authService.signInWithGoogle(context)
            callback(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    // ✅ Get real-time note updates
    fun getCollaborativeNoteFlow(noteId: String): Flow<CollaborativeNote?> {
        return collaborationService.getCollaborativeNoteFlow(noteId)
    }

    // ✅ Load collaborative note - FIXED
    fun loadCollaborativeNote(noteId: String, callback: (Result<CollaborativeNote?>) -> Unit) {
        viewModelScope.launch {
            val result = collaborationService.getCollaborativeNoteById(noteId)
            callback(result)
        }
    }

    fun forceRemoveCollaborativeNote(collaborativeId: String) {
        viewModelScope.launch {
            try {
                Log.d("NoteViewModel", "🔧 DEBUG: Force removing collaborative note: $collaborativeId")

                // Try the existing method first
                try {
                    val result = collaborationService.removeUserFromCollaborativeNote(collaborativeId)
                    if (result.isSuccess) {
                        _uiEvent.send(UiEvent.ShowMessage("Successfully removed collaborative note"))
                        startCollaborativeSync()
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("NoteViewModel", "removeUserFromCollaborativeNote failed: ${e.message}")
                }

                // If that fails, try to manually remove from local state
                try {
                    val currentNotes = _collaborativeNotes.value.toMutableList()
                    val removed = currentNotes.removeAll { it.id == collaborativeId }
                    if (removed) {
                        _collaborativeNotes.value = currentNotes
                        _uiEvent.send(UiEvent.ShowMessage("Force removed collaborative note from local state"))
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("NoteViewModel", "Local removal failed: ${e.message}")
                }

                // If all else fails
                _uiEvent.send(UiEvent.ShowError("Unable to remove collaborative note. Please try again later."))

            } catch (e: Exception) {
                Log.e("NoteViewModel", "🔧 DEBUG: Force remove failed with exception: ${e.message}")
                _uiEvent.send(UiEvent.ShowError("Error: ${e.message}"))
            }
        }
    }

    // ✅ FIXED: Leave collaborative note
    fun leaveCollaborativeNote(collaborativeId: String) {
        viewModelScope.launch {
            try {
                Log.d("NoteViewModel", "🔧 DEBUG: Leaving collaborative note: $collaborativeId")
                val result = collaborationService.removeUserFromCollaborativeNote(collaborativeId)
                if (result.isSuccess) {
                    _uiEvent.send(UiEvent.ShowMessage("Left collaborative note successfully"))
                    startCollaborativeSync()
                } else {
                    _uiEvent.send(UiEvent.ShowError("Failed to leave note. Trying force removal..."))
                    // If regular leave fails, try force remove
                    forceRemoveCollaborativeNote(collaborativeId)
                }
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.ShowError("Error leaving note. Trying force removal..."))
                // If regular leave fails, try force remove
                forceRemoveCollaborativeNote(collaborativeId)
            }
        }
    }

    // ✅ Get collaborative note by ID - FIXED
    fun getCollaborativeNoteById(
        noteId: String,
        onResult: (Result<CollaborativeNote?>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d("NoteViewModel", "Fetching collaborative note: $noteId")
                val result = collaborationService.getCollaborativeNoteById(noteId)
                result.onSuccess { note ->
                    Log.d("NoteViewModel", "Fetched note: ${note?.title}")
                    onResult(Result.success(note))
                }.onFailure { error ->
                    Log.e("NoteViewModel", "Error fetching collaborative note", error)
                    onResult(Result.failure(error))
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error fetching collaborative note", e)
                onResult(Result.failure(e))
            }
        }
    }


    // Firebase Auth functions
    fun signUp(email: String, password: String, displayName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authService.signUp(email, password, displayName)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun signIn(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authService.signIn(email, password)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    // ✅ Sign in with Google - FIXED
    fun signInWithGoogle(context: Context, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = authService.signInWithGoogle(context)
            callback(result.isSuccess)
        }
    }

    fun signOut() {
        authService.signOut()
        // Clear collaborative data when signing out
        _collaborativeNotes.value = emptyList()
        _pendingInvites.value = emptyList()
        _collaborationError.value = null
    }

    fun isUserSignedIn(): Boolean = authService.isUserSignedIn()
    fun getCurrentUserEmail(): String? = authService.getCurrentUserEmail()

    // ✅ Create test collaborative note - FIXED
    fun createTestCollaborativeNote(callback: (String) -> Unit) {
        viewModelScope.launch {
            val result = collaborationService.createCollaborativeNote(
                "Test Collaborative Note",
                "[]",
                "CHECKLIST"
            )
            result.fold(
                onSuccess = { noteId -> callback(noteId) },
                onFailure = {
                    _collaborationError.value = "Failed to create test note: ${it.message}"
                }
            )
        }
    }

    // ✅ Send test invitation - FIXED
    fun sendTestInvitation(noteId: String, email: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = collaborationService.sendInvitation(noteId, email)
            callback(result.isSuccess)
        }
    }

    // ✅ Refresh collaborative data - FIXED
    fun refreshCollaborativeData() {
        viewModelScope.launch {
            startCollaborativeSync()
        }
    }

    // Test Firebase connection
    fun testFirebaseConnection() {
        viewModelScope.launch {
            try {
                if (!authService.isUserSignedIn()) {
                    Log.d("Firebase", "User not signed in")
                    return@launch
                }

                Log.d("Firebase", "User is signed in: ${authService.getCurrentUserEmail()}")

                // Test creating a note
                val result = collaborationService.createCollaborativeNote(
                    "Test Collaborative Note",
                    "[]",
                    "CHECKLIST"
                )

                result.onSuccess { noteId ->
                    Log.d("Firebase", "Successfully created note: $noteId")
                }.onFailure { error ->
                    Log.e("Firebase", "Failed to create note", error)
                }
            } catch (e: Exception) {
                Log.e("Firebase", "Firebase test failed", e)
            }
        }
    }

    // AUTO-CLEANUP FUNCTIONALITY
    private fun startAutoCleanup() {
        cleanupJob?.cancel()
        cleanupJob = viewModelScope.launch {
            while (true) {
                try {
                    performCleanup()
                    delay(CLEANUP_CHECK_INTERVAL)
                } catch (e: Exception) {
                    // Log error but continue cleanup cycle
                    delay(CLEANUP_CHECK_INTERVAL)
                }
            }
        }
    }

    private suspend fun performCleanup() {
        try {
            val cutoffTime = System.currentTimeMillis() - BIN_RETENTION_MILLIS
            val expiredCount = noteDao.getExpiredNotesCount(cutoffTime)

            if (expiredCount > 0) {
                noteDao.deleteExpiredNotes(cutoffTime)
                _uiEvent.send(UiEvent.ShowMessage(
                    getApplication<Application>().getString(R.string.msg_expired_notes_deleted, expiredCount)
                ))
            }
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error during cleanup", e)
        }
    }

    /**
     * MANUAL CLEANUP - Can be called from UI
     */
    fun performManualCleanup() = viewModelScope.launch {
        try {
            val cutoffTime = System.currentTimeMillis() - BIN_RETENTION_MILLIS
            val expiredNotes = noteDao.getExpiredNotes(cutoffTime)

            if (expiredNotes.isNotEmpty()) {
                noteDao.deleteExpiredNotes(cutoffTime)
                _uiEvent.send(UiEvent.ShowMessage(
                    getApplication<Application>().getString(R.string.msg_expired_notes_cleaned, expiredNotes.size)
                ))
            } else {
                _uiEvent.send(UiEvent.ShowMessage(
                    getApplication<Application>().getString(R.string.msg_no_expired_notes)
                ))
            }
        } catch (e: Exception) {
            _uiEvent.send(UiEvent.ShowError("Cleanup failed: ${e.message}"))
        }
    }

    /**
     * GET DAYS REMAINING FOR A NOTE IN BIN
     */
    fun getDaysUntilExpiry(note: Note): Int? {
        if (!note.isDeleted || note.deletedAt == null) return null

        val daysSinceDeletion = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - note.deletedAt
        )
        return (BIN_RETENTION_DAYS - daysSinceDeletion).toInt().coerceAtLeast(0)
    }

    /**
     * CHECK IF NOTE IS EXPIRED
     */
    fun isNoteExpired(note: Note): Boolean {
        if (!note.isDeleted || note.deletedAt == null) return false

        val cutoffTime = System.currentTimeMillis() - BIN_RETENTION_MILLIS
        return note.deletedAt < cutoffTime
    }

    /**
     * Core save functionality with enhanced archive support and color preservation
     */
    private suspend fun performSave(id: Int, title: String, content: String, type: NoteType, colorIndex: Int? = null): Int {
        val currentTime = System.currentTimeMillis()
        val context = getApplication<Application>()
        val noteToSave = if (id != 0) {
            val existingNote = noteDao.getNoteById(id).firstOrNull()
            Note(
                id = id,
                title = title.ifBlank {
                    existingNote?.title?.ifBlank {
                        if (type == NoteType.CHECKLIST) context.getString(R.string.untitled_todo) else context.getString(R.string.untitled_note)
                    } ?: if (type == NoteType.CHECKLIST) context.getString(R.string.untitled_todo) else context.getString(R.string.untitled_note)
                },
                content = content,
                type = type,
                createdAt = existingNote?.createdAt ?: currentTime,
                lastModifiedAt = currentTime,
                isArchived = existingNote?.isArchived ?: false,
                isDeleted = existingNote?.isDeleted ?: false,
                deletedAt = existingNote?.deletedAt,
                colorIndex = colorIndex ?: existingNote?.colorIndex ?: 0,
                isPinned = existingNote?.isPinned ?: false,
                categoryId = existingNote?.categoryId,
                orderIndex = existingNote?.orderIndex ?: 0
            )
        } else {
            Note(
                title = title.ifBlank {
                    if (type == NoteType.CHECKLIST) context.getString(R.string.untitled_todo) else context.getString(R.string.untitled_note)
                },
                content = content,
                type = type,
                createdAt = currentTime,
                lastModifiedAt = currentTime,
                isArchived = false,
                isDeleted = false,
                deletedAt = null,
                colorIndex = colorIndex ?: 0,
                isPinned = false,
                categoryId = null,
                orderIndex = 0
            )
        }
        return noteDao.insertOrUpdate(noteToSave).toInt()
    }

    /**
     * Final save when user leaves the note
     */
    fun finaliseAndSave(noteId: Int, title: String, contentJson: String, noteType: NoteType): Job {
        autoSaveJob?.cancel()
        return viewModelScope.launch {
            // Don't save empty notes
            if (noteId == 0 && title.isBlank() && (contentJson.isBlank() || contentJson == "[]")) {
                return@launch
            }
            performSave(noteId, title, contentJson, noteType)
        }
    }

    /**
     * Auto-save functionality while user is typing
     */
    fun triggerAutoSave(
        noteId: Int,
        title: String,
        lines: List<NoteLine>,
        noteType: NoteType,
        onIdReceived: (Int) -> Unit
    ) {
        autoSaveJob?.cancel()

        val isContentEmpty = title.isBlank() && lines.all { it.content.isBlank() }
        if (noteId == 0 && isContentEmpty) {
            return
        }

        autoSaveJob = viewModelScope.launch {
            delay(autoSaveDelay)
            val contentJson = lines.toJson()
            val savedId = performSave(noteId, title, contentJson, noteType)
            if (savedId != 0) {
                onIdReceived(savedId)
            }
        }
    }

    /**
     * Update note color
     */
    fun updateNoteColor(note: Note, colorIndex: Int) = viewModelScope.launch {
        val updatedNote = note.copy(
            colorIndex = colorIndex,
            lastModifiedAt = System.currentTimeMillis()
        )
        noteDao.insertOrUpdate(updatedNote)
        _uiEvent.send(UiEvent.ShowMessage(
            getApplication<Application>().getString(R.string.msg_note_color_updated)
        ))
    }

    /**
     * Batch update colors for multiple notes
     */
    fun updateMultipleNotesColor(notes: List<Note>, colorIndex: Int) = viewModelScope.launch {
        val currentTime = System.currentTimeMillis()
        notes.forEach { note ->
            val updatedNote = note.copy(
                colorIndex = colorIndex,
                lastModifiedAt = currentTime
            )
            noteDao.insertOrUpdate(updatedNote)
        }

        val message = if (notes.size == 1) {
            getApplication<Application>().getString(R.string.msg_note_color_updated)
        } else {
            getApplication<Application>().getString(R.string.msg_notes_color_updated, notes.size)
        }
        _uiEvent.send(UiEvent.ShowMessage(message))
    }

    /**
     * Get notes by color
     */
    fun getNotesByColor(colorIndex: Int): Flow<List<Note>> {
        return noteDao.getAllNotes().map { notes ->
            notes.filter { !it.isArchived && !it.isDeleted && it.colorIndex == colorIndex }
        }
    }

    fun updateNoteTitle(note: Note, newTitle: String) = viewModelScope.launch {
        val updatedNote = note.copy(
            title = newTitle
            // Don't update lastModifiedAt when just changing the title
        )
        noteDao.insertOrUpdate(updatedNote)
    }

    // In NoteViewModel
    fun createNoteWithRandomColor(title: String, content: String, type: NoteType): Job {
        return viewModelScope.launch {
            val randomColorIndex = NoteColorPalette.getRandomColor()
            performSave(0, title, content, type, randomColorIndex)
        }
    }

    /**
     * Delete note by ID (used in detail screens) - UPDATED WITH TIMESTAMP
     */
    fun deleteNoteById(id: Int) = viewModelScope.launch {
        autoSaveJob?.cancel()
        noteDao.getNoteById(id).firstOrNull()?.let { noteToDelete ->
            recentlyDeletedNote = noteToDelete
            val currentTime = System.currentTimeMillis()
            val deletedNote = noteToDelete.copy(
                isDeleted = true,
                deletedAt = currentTime,
                lastModifiedAt = currentTime
            )
            noteDao.insertOrUpdate(deletedNote)
            _uiEvent.send(UiEvent.ShowUndoDeleteSnackbar)
        }
    }

    /**
     * Get a specific note by ID
     */
    fun getNoteById(id: Int): Flow<Note?> {
        return noteDao.getNoteById(id)
    }


    /**
     * Update search query
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * Delete note (triggered by swipe or button) - UPDATED WITH TIMESTAMP
     */
    fun triggerDelete(note: Note) = viewModelScope.launch {
        recentlyDeletedNote = note
        val currentTime = System.currentTimeMillis()
        val deletedNote = note.copy(
            isDeleted = true,
            deletedAt = currentTime,
            lastModifiedAt = currentTime
        )
        noteDao.insertOrUpdate(deletedNote)
        _uiEvent.send(UiEvent.ShowUndoDeleteSnackbar)
    }

    /**
     * Archive note (triggered by swipe or button)
     */
    fun triggerArchive(note: Note) = viewModelScope.launch {
        recentlyArchivedNote = note
        val archivedNote = note.copy(
            isArchived = true,
            lastModifiedAt = System.currentTimeMillis()
        )
        noteDao.insertOrUpdate(archivedNote)
        _uiEvent.send(UiEvent.ShowUndoArchiveSnackbar)
    }

    /**
     * Unarchive note (restore from archive)
     */
    fun triggerUnarchive(note: Note) = viewModelScope.launch {
        val unarchivedNote = note.copy(
            isArchived = false,
            lastModifiedAt = System.currentTimeMillis()
        )
        noteDao.insertOrUpdate(unarchivedNote)
        _uiEvent.send(UiEvent.ShowMessage(
            getApplication<Application>().getString(R.string.msg_note_restored_from_archive)
        ))
    }

    /**
     * Handle swipe actions based on user settings
     */
    fun handleSwipeAction(note: Note, action: SwipeAction) {
        when (action) {
            SwipeAction.DELETE -> triggerDelete(note)
            SwipeAction.ARCHIVE -> triggerArchive(note)
            SwipeAction.NONE -> { /* Do nothing */ }
        }
    }

    /**
     * Undo delete operation - UPDATED TO CLEAR TIMESTAMP
     */
    fun undoDelete() = viewModelScope.launch {
        recentlyDeletedNote?.let { note ->
            val restoredNote = note.copy(
                isDeleted = false,
                deletedAt = null,
                lastModifiedAt = System.currentTimeMillis()
            )
            noteDao.insertOrUpdate(restoredNote)
            recentlyDeletedNote = null
        }
    }

    /**
     * Undo archive operation
     */
    fun undoArchive() = viewModelScope.launch {
        recentlyArchivedNote?.let { note ->
            val unarchivedNote = note.copy(
                isArchived = false,
                lastModifiedAt = System.currentTimeMillis()
            )
            noteDao.insertOrUpdate(unarchivedNote)
            recentlyArchivedNote = null
        }
    }

    /**
     * Permanently delete note (from bin/trash)
     */
    fun permanentlyDelete(note: Note) = viewModelScope.launch {
        noteDao.delete(note)
        _uiEvent.send(UiEvent.ShowMessage(
            getApplication<Application>().getString(R.string.msg_note_permanently_deleted)
        ))
    }

    /**
     * Restore note from bin/trash - UPDATED TO CLEAR TIMESTAMP
     */
    fun restoreNote(note: Note) = viewModelScope.launch {
        val restoredNote = note.copy(
            isDeleted = false,
            isArchived = false,
            deletedAt = null,
            lastModifiedAt = System.currentTimeMillis()
        )
        noteDao.insertOrUpdate(restoredNote)
        _uiEvent.send(UiEvent.ShowMessage(
            getApplication<Application>().getString(R.string.msg_note_restored)
        ))
    }

    /**
     * Restore all archived notes
     */
    fun restoreAllArchivedNotes() = viewModelScope.launch {
        val archived = archivedNotes.value
        archived.forEach { note ->
            val restoredNote = note.copy(
                isArchived = false,
                lastModifiedAt = System.currentTimeMillis()
            )
            noteDao.insertOrUpdate(restoredNote)
        }
    }

    /**
     * Restore all deleted notes - UPDATED TO CLEAR TIMESTAMPS
     */
    fun restoreAllDeletedNotes() = viewModelScope.launch {
        val deleted = deletedNotes.value
        deleted.forEach { note ->
            val restoredNote = note.copy(
                isDeleted = false,
                isArchived = false,
                deletedAt = null,
                lastModifiedAt = System.currentTimeMillis()
            )
            noteDao.insertOrUpdate(restoredNote)
        }
    }

    /**
     * Empty bin (permanently delete all notes in bin)
     */
    fun emptyBin() = viewModelScope.launch {
        val deleted = deletedNotes.value
        deleted.forEach { note ->
            noteDao.delete(note)
        }
    }


    /**
     * Toggle note pin
     */
    fun toggleNotePin(note: Note) = viewModelScope.launch {
        val updatedNote = note.copy(
            isPinned = !note.isPinned,
            lastModifiedAt = note.lastModifiedAt // Don't update lastModifiedAt when pinning
        )
        noteDao.insertOrUpdate(updatedNote)
        _uiEvent.send(
            UiEvent.ShowMessage(
                if (updatedNote.isPinned) {
                    getApplication<Application>().getString(R.string.msg_note_pinned)
                } else {
                    getApplication<Application>().getString(R.string.msg_note_unpinned)
                }
            )
        )
    }

    /**
     * Create category
     */
    fun createCategory(name: String) = viewModelScope.launch {
        val category = Category(name = name, order = allCategories.value.size)
        categoryDao.insert(category)
        _uiEvent.send(UiEvent.ShowMessage(
            getApplication<Application>().getString(R.string.msg_category_created, name)
        ))
    }

    /**
     * Update category
     */
    fun updateCategory(category: Category) = viewModelScope.launch {
        categoryDao.update(category)
        _uiEvent.send(UiEvent.ShowMessage(
            getApplication<Application>().getString(R.string.msg_category_updated)
        ))
    }

    /**
     * Delete category
     */
    fun deleteCategory(category: Category) = viewModelScope.launch {
        // Move all notes from this category to uncategorized
        val notesInCategory = allNotes.value.filter { it.categoryId == category.id }
        notesInCategory.forEach { note ->
            noteDao.insertOrUpdate(note.copy(categoryId = null))
        }

        categoryDao.delete(category)
        _uiEvent.send(UiEvent.ShowMessage(
            getApplication<Application>().getString(R.string.msg_category_deleted)
        ))
    }

    /**
     * Move note to category - ALREADY HANDLES UNPINNING
     */
    fun moveNoteToCategory(note: Note, categoryId: Int?) = viewModelScope.launch {
        val updatedNote = note.copy(
            categoryId = categoryId,
            isPinned = false  // Automatically unpin when moving to a category
        )
        noteDao.insertOrUpdate(updatedNote)

        val message = if (categoryId != null) {
            val category = categoryDao.getCategoryById(categoryId)
            getApplication<Application>().getString(R.string.msg_note_moved_to_category, category?.name ?: "category")
        } else {
            getApplication<Application>().getString(R.string.msg_note_moved_to_uncategorized)
        }
        _uiEvent.send(UiEvent.ShowMessage(message))
    }

    /**
     * Clear all auto-save and cleanup jobs when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        cleanupJob?.cancel()
    }

    /**
     * UI Events for showing snackbars and other UI feedback
     */
    sealed class UiEvent {
        data object ShowUndoDeleteSnackbar : UiEvent()
        data object ShowUndoArchiveSnackbar : UiEvent()
        data class ShowMessage(val message: String) : UiEvent()
        data class ShowError(val error: String) : UiEvent()
    }

    /**
     * Helper function to send UI events
     */
    private suspend fun sendUiEvent(event: UiEvent) {
        _uiEvent.send(event)
    }

    /**
     * Show a general message
     */
    fun showMessage(message: String) = viewModelScope.launch {
        sendUiEvent(UiEvent.ShowMessage(message))
    }


    // ========================================
    // GOOGLE DRIVE BACKUP METHODS
    // ========================================

    /**
     * Get all notes for backup (excludes deleted notes)
     */
    fun getAllNotesForBackup(): List<Note> {
        // Since we're already in a ViewModel, we can use the existing data
        // Combine active notes and archived notes
        val activeNotes = allNotes.value
        val archivedNotes = archivedNotes.value

        // Return all notes (both active and archived)
        return activeNotes + archivedNotes
    }

    /**
     * Get only active notes for backup (excludes archived and deleted)
     */
    suspend fun getActiveNotesForBackup(): List<Note> {
        return noteDao.getAllNotes().first().filter { !it.isArchived && !it.isDeleted }
    }

    /**
     * Add restored note from backup
     */
    fun addRestoredNote(note: Note) {
        viewModelScope.launch {
            try {
                // Create a new note with a new ID to avoid conflicts
                val restoredNote = note.copy(
                    id = 0, // Room will auto-generate a new ID
                    lastModifiedAt = System.currentTimeMillis()
                )
                noteDao.insertOrUpdate(restoredNote)
            } catch (e: Exception) {
                // Handle error if needed
            }
        }
    }
}



// ========================================
// BACKUP DATA CLASSES
// ========================================

/**
 * Backup metadata container
 */
data class BackupMetadata(
    val totalNotes: Int,
    val activeNotes: Int,
    val archivedNotes: Int,
    val notes: List<Note>,
    val backupTimestamp: Long,
    val appVersion: String
)

/**
 * Backup validation result
 */
data class BackupValidationResult(
    val validNotes: List<Note>,
    val invalidNotes: List<String>,
    val isValid: Boolean
)

/**
 * Backup summary for UI display
 */
data class BackupSummary(
    val totalNotes: Int,
    val activeNotes: Int,
    val archivedNotes: Int,
    val deletedNotes: Int,
    val colorDistribution: Map<Int, Int>,
    val typeDistribution: Map<NoteType, Int>,
    val lastModified: Long
)
