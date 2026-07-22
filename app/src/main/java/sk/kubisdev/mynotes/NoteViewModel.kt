package sk.kubisdev.mynotes

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.kubisdev.mynotes.backup.BackupData
import sk.kubisdev.mynotes.data.importer.NoteImportManager
import sk.kubisdev.mynotes.data.remote.firebase.AuthService
import sk.kubisdev.mynotes.data.remote.firebase.CollaborationService
import sk.kubisdev.mynotes.data.remote.local.NoteDatabase
import sk.kubisdev.mynotes.data.remote.local.entities.Category
import sk.kubisdev.mynotes.data.remote.local.entities.Note
import sk.kubisdev.mynotes.data.remote.local.entities.NoteType
import sk.kubisdev.mynotes.settings.model.SwipeAction
import sk.kubisdev.mynotes.data.remote.models.CollaborativeNote
import sk.kubisdev.mynotes.data.remote.models.NoteInvite
import sk.kubisdev.mynotes.reminder.ReminderScheduler
import sk.kubisdev.mynotes.NoteLine
import sk.kubisdev.mynotes.R
import sk.kubisdev.mynotes.ui.theme.NoteColorPalette
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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

    // Store recently deleted/archived notes for undo functionality.
    // Deletions triggered in quick succession (e.g. swiping through several notes)
    // are batched: each queueNoteDeletion() call restarts a short debounce timer,
    // so only one snackbar is shown for the whole burst instead of one per note.
    private val pendingDeletedNotes = mutableListOf<Note>()
    private var lastDeletedBatch: List<Note> = emptyList()
    private var deleteSnackbarJob: Job? = null
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

    private var syncJob: Job? = null
    private var inviteSyncJob: Job? = null

    init {
        // START AUTO-CLEANUP ON INIT
        startAutoCleanup()
    }

    // Get all active notes (filtering archived and deleted ones)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allNotes: StateFlow<List<Note>> = searchQuery
        .flatMapLatest { query ->
            noteDao.getAllNotes().map { notes ->
                val active = notes.filter { !it.isArchived && !it.isDeleted }
                if (query.isBlank()) {
                    active
                } else {
                    // Diacritic-insensitive: matches "Nakupny" against "Nákupný".
                    val normalizedQuery = query.normalizeForSearch()
                    active.filter {
                        it.title.normalizeForSearch().contains(normalizedQuery) ||
                                it.content.normalizeForSearch().contains(normalizedQuery)
                    }
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
        // Don't start if already syncing
        if (syncJob?.isActive == true && inviteSyncJob?.isActive == true) return

        // Mirror the Google account's current name/avatar into the shared users
        // collection so collaborators see a real photo instead of the fallback glyph.
        viewModelScope.launch { authService.syncCurrentUserProfile() }

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            try {
                collaborationService.getUserCollaborativeNotes()
                    .collect { notes ->
                        _collaborativeNotes.value = notes
                    }
            } catch (e: Exception) {
                _collaborationError.value = "Failed to sync collaborative notes: ${e.message}"
            }
        }

        inviteSyncJob?.cancel()
        inviteSyncJob = viewModelScope.launch {
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
                // Image lines hold device-local file:// URIs that no other collaborator
                // could ever load, so they're dropped when a note goes collaborative
                // (the editor also hides the image-insert option in shared notes).
                val content = lines.filter { it.type != LineType.IMAGE }.toJson()
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

    /**
     * Saves a private, device-local snapshot of a collaborative note.
     *
     * Shared notes live only in Firestore under the owner's account: they aren't part
     * of the Google Drive backup and disappear for everyone if the owner deletes them.
     * This gives each member a plain local note they keep regardless of what happens to
     * the shared original. The copy is a detached snapshot - it never syncs back.
     */
    fun saveCollaborativeNoteLocally(
        title: String,
        lines: List<NoteLine>,
        noteType: NoteType,
        onSaved: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // Image lines are device-local file:// URIs from another member's phone,
                // so they're dropped exactly like when a note is made collaborative.
                val content = lines.filter { it.type != LineType.IMAGE }.toJson()
                val newId = performSave(0, title, content, noteType, NoteColorPalette.getRandomColor())
                onSaved(newId)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.ShowError("Failed to save local copy: ${e.message}"))
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
            updateCollaborativeNoteAndAwait(noteId, title, lines)
        }
    }

    // Suspending variant for callers that need to know the Firestore write has actually
    // finished (not just been dispatched) before acting - e.g. clearing an "actively
    // editing" flag that guards against the live-sync listener overwriting in-progress
    // edits. The fire-and-forget version above returns as soon as its own coroutine is
    // *launched*, which is effectively instantly - anyone gating on that instead of on
    // real completion reopens the exact race it was meant to close.
    suspend fun updateCollaborativeNoteAndAwait(
        noteId: String,
        title: String,
        lines: List<NoteLine>
    ) {
        try {
            val content = lines.toJson()
            val result = collaborationService.updateCollaborativeNote(noteId, title, content)
            result.onFailure { error ->
                _collaborationError.value = "Failed to update note: ${error.message}"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Not a real failure - triggerImmediateAutoSave cancels a still-in-flight
            // debounced save whenever a newer edit supersedes it (autoSaveJob?.cancel()).
            // Catching this as Exception below and reporting it as "failed to update" was
            // actively harmful: it swallowed the cancellation instead of letting it
            // propagate, so the caller's coroutine carried on past the awaited call and
            // cleared isUserEditing even though THIS save never completed - reopening the
            // exact live-sync race this suspend function exists to close (a stale Firestore
            // snapshot could then overwrite `lines`, silently reverting the user's edit).
            throw e
        } catch (e: Exception) {
            _collaborationError.value = "Failed to update note: ${e.message}"
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

    // ✅ Delete collaborative note
    fun deleteCollaborativeNote(collaborativeId: String) {
        viewModelScope.launch {
            try {
                Log.d("NoteViewModel", "Deleting collaborative note: $collaborativeId")
                val result = collaborationService.deleteCollaborativeNote(collaborativeId)
                if (result.isSuccess) {
                    _uiEvent.send(UiEvent.ShowMessage("Collaborative note deleted successfully"))
                    startCollaborativeSync()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    if (errorMsg.contains("Only the owner")) {
                        // If not owner, just leave the note
                        leaveCollaborativeNote(collaborativeId)
                    } else {
                        _uiEvent.send(UiEvent.ShowError("Failed to delete note: $errorMsg"))
                    }
                }
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.ShowError("Error deleting note: ${e.message}"))
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
    fun getCurrentUserId(): String? = authService.getCurrentUserId()
    fun getCurrentUserEmail(): String? = authService.getCurrentUserEmail()

    // Loads the profiles (photo URLs) for a collaborative note's members, so shared
    // checklist lines can show who edited each one.
    fun loadCollaboratorProfiles(
        userIds: List<String>,
        onResult: (Map<String, sk.kubisdev.mynotes.data.remote.models.UserProfile>) -> Unit
    ) {
        viewModelScope.launch {
            val profiles = collaborationService.getUserProfiles(userIds)
            onResult(profiles)
        }
    }

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
                orderIndex = existingNote?.orderIndex ?: 0,
                // This function rebuilds the Note field-by-field, so every field NOT
                // listed here silently resets to its default on every autosave -
                // that's exactly how the background pattern (and the collaborative
                // link) were getting wiped the moment the user typed anything.
                patternIndex = existingNote?.patternIndex ?: 0,
                collaborativeNoteId = existingNote?.collaborativeNoteId,
                // Same trap as patternIndex above: omitting this wipes a set reminder on
                // the first autosave after the user types anything in the note.
                reminderAt = existingNote?.reminderAt
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
    fun finaliseAndSave(noteId: Int, title: String, contentJson: String, noteType: NoteType, colorIndex: Int? = null): Job {
        autoSaveJob?.cancel()
        return viewModelScope.launch {
            // Don't save empty notes
            if (noteId == 0 && title.isBlank() && (contentJson.isBlank() || contentJson == "[]")) {
                return@launch
            }
            performSave(noteId, title, contentJson, noteType, colorIndex)
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
        colorIndex: Int? = null,
        onIdReceived: (Int) -> Unit
    ) {
        autoSaveJob?.cancel()

        val isContentEmpty = title.isBlank() && lines.all { it.content.isBlank() }
        if (noteId == 0 && isContentEmpty) {
            return
        }

        autoSaveJob = viewModelScope.launch {
            delay(autoSaveDelay)
            val contentJson = withContext(Dispatchers.Default) { lines.toJson() }
            val savedId = performSave(noteId, title, contentJson, noteType, colorIndex)
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
     * Update note background pattern (see NotePatterns)
     */
    fun updateNotePattern(note: Note, patternIndex: Int) = viewModelScope.launch {
        noteDao.insertOrUpdate(
            note.copy(patternIndex = patternIndex, lastModifiedAt = System.currentTimeMillis())
        )
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
            val currentTime = System.currentTimeMillis()
            val deletedNote = noteToDelete.copy(
                isDeleted = true,
                deletedAt = currentTime,
                lastModifiedAt = currentTime
            )
            noteDao.insertOrUpdate(deletedNote)
            if (noteToDelete.reminderAt != null) {
                ReminderScheduler.cancel(getApplication(), noteToDelete.id)
            }
            queueNoteDeletion(noteToDelete)
        }
    }

    // Accumulates a note into the pending delete batch and (re)starts the
    // debounce timer; the snackbar only fires once the burst of deletions settles.
    private fun queueNoteDeletion(note: Note) {
        pendingDeletedNotes.add(note)
        deleteSnackbarJob?.cancel()
        deleteSnackbarJob = viewModelScope.launch {
            delay(400)
            lastDeletedBatch = pendingDeletedNotes.toList()
            pendingDeletedNotes.clear()
            _uiEvent.send(UiEvent.ShowUndoDeleteSnackbar(lastDeletedBatch.size))
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
    /**
     * Import notes from an external file (Evernote .enex, Google Keep Takeout .zip/.json,
     * or plain .txt/.md). Format is auto-detected from the file name.
     */
    fun importNotes(uri: Uri) = viewModelScope.launch {
        try {
            val context = getApplication<Application>()
            val imported = withContext(Dispatchers.IO) {
                NoteImportManager.importFromUri(context, uri)
            }
            withContext(Dispatchers.IO) {
                imported.forEach { imported ->
                    val currentTime = System.currentTimeMillis()
                    noteDao.insertOrUpdate(
                        Note(
                            title = imported.title,
                            content = imported.lines.toJson(),
                            type = imported.type,
                            createdAt = currentTime,
                            lastModifiedAt = currentTime
                        )
                    )
                }
            }
            _uiEvent.send(UiEvent.ShowMessage("Imported ${imported.size} note(s)"))
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Import failed", e)
            _uiEvent.send(UiEvent.ShowError("Import failed: ${e.message}"))
        }
    }

    /**
     * Restores a MyNotes backup (.json) picked from local storage, instead of from
     * Google Drive. This is the recovery path when a backup can no longer be listed on
     * Drive - e.g. after a package-name change, when the `drive.file` scope stops
     * exposing files created by the old OAuth client. The user downloads the backup
     * JSON from Drive manually and picks it here; the file itself is package-independent
     * (just notes + categories), so it restores with full fidelity - colors, pins,
     * archive state, groups and patterns are all preserved (unlike the third-party
     * importers, which only carry title/lines/type).
     *
     * Merge semantics mirror BackupViewModel.restoreFromBackup's default: categories land
     * first (remapping ids), then only notes not already present locally (by
     * title+content+createdAt) are added, so re-importing the same file is a no-op
     * instead of duplicating everything.
     */
    fun restoreFromLocalBackup(uri: Uri) = viewModelScope.launch {
        val context = getApplication<Application>()
        try {
            val backupData = withContext(Dispatchers.IO) {
                val json = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw Exception(context.getString(R.string.import_backup_read_failed))
                com.google.gson.Gson().fromJson(json, BackupData::class.java)
            } ?: throw Exception(context.getString(R.string.import_backup_invalid))

            // A random (non-MyNotes) JSON deserializes into a BackupData whose `notes`
            // field is null - guard so the user gets a clear "not a MyNotes backup"
            // message instead of a confusing NullPointerException deep in the loop.
            @Suppress("SENSELESS_COMPARISON")
            if (backupData.notes == null) {
                throw Exception(context.getString(R.string.import_backup_invalid))
            }

            val categoryIdMap = restoreCategories(backupData.categories ?: emptyList())
            val notesToRestore = filterOutExistingNotes(backupData.notes)
            val skippedDuplicates = backupData.notes.size - notesToRestore.size

            var restoredCount = 0
            notesToRestore.forEach { note ->
                if (addRestoredNote(note, categoryIdMap)) restoredCount++
            }

            val message = if (skippedDuplicates > 0) {
                context.getString(R.string.import_backup_success_with_duplicates, restoredCount, skippedDuplicates)
            } else {
                context.getString(R.string.import_backup_success, restoredCount)
            }
            _uiEvent.send(UiEvent.ShowMessage(message))
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Local backup restore failed", e)
            _uiEvent.send(UiEvent.ShowError(context.getString(R.string.import_backup_failed, e.message ?: "")))
        }
    }

    fun triggerDelete(note: Note) = viewModelScope.launch {
        val currentTime = System.currentTimeMillis()
        val deletedNote = note.copy(
            isDeleted = true,
            deletedAt = currentTime,
            lastModifiedAt = currentTime
        )
        noteDao.insertOrUpdate(deletedNote)
        // Don't let a reminder fire for a note the user just sent to the bin. The
        // reminderAt value is kept so restoreNote() can re-arm it.
        if (note.reminderAt != null) {
            ReminderScheduler.cancel(getApplication(), note.id)
        }
        queueNoteDeletion(note)
    }

    // ========================================
    // NOTE REMINDERS
    // ========================================

    /**
     * Sets (or moves) a one-shot reminder on a note and arms the exact alarm. Keeps the
     * note's lastModifiedAt unchanged so adding a reminder doesn't reorder the list.
     */
    fun setReminder(noteId: Int, timeMillis: Long) = viewModelScope.launch {
        if (noteId == 0) return@launch
        val context = getApplication<Application>()
        // A custom-picked date/time can land in the past; reject it instead of arming an
        // alarm that would fire immediately (or never).
        if (timeMillis <= System.currentTimeMillis()) {
            _uiEvent.send(UiEvent.ShowError(context.getString(R.string.reminder_past_time_error)))
            return@launch
        }
        val note = noteDao.getNoteById(noteId).firstOrNull() ?: return@launch
        noteDao.insertOrUpdate(note.copy(reminderAt = timeMillis))
        ReminderScheduler.schedule(context, noteId, note.title, note.type.name, timeMillis)
        // If the OS won't let us schedule exact alarms, the reminder still works but may
        // arrive late - tell the user how to make it precise.
        if (!ReminderScheduler.canScheduleExact(context)) {
            _uiEvent.send(UiEvent.ShowMessage(context.getString(R.string.reminder_inexact_hint)))
        }
    }

    /**
     * Removes a note's reminder and cancels its pending alarm.
     */
    fun clearReminder(noteId: Int) = viewModelScope.launch {
        if (noteId == 0) return@launch
        val context = getApplication<Application>()
        noteDao.clearReminder(noteId)
        ReminderScheduler.cancel(context, noteId)
        _uiEvent.send(UiEvent.ShowMessage(context.getString(R.string.reminder_removed)))
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
        if (lastDeletedBatch.isEmpty()) return@launch
        val currentTime = System.currentTimeMillis()
        lastDeletedBatch.forEach { note ->
            noteDao.insertOrUpdate(
                note.copy(isDeleted = false, deletedAt = null, lastModifiedAt = currentTime)
            )
        }
        lastDeletedBatch = emptyList()
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
        if (note.reminderAt != null) {
            ReminderScheduler.cancel(getApplication(), note.id)
        }
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
        // Re-arm a still-future reminder that was cancelled when the note went to the bin.
        val reminderAt = restoredNote.reminderAt
        if (reminderAt != null && reminderAt > System.currentTimeMillis()) {
            ReminderScheduler.schedule(
                getApplication(), restoredNote.id, restoredNote.title, restoredNote.type.name, reminderAt
            )
        }
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
        data class ShowUndoDeleteSnackbar(val count: Int) : UiEvent()
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
     * Get all categories/groups for backup, including empty ones - the backup file
     * only ever contained notes before, so an empty group (or one with no notes left
     * by the time of backup) was silently never restored.
     */
    fun getAllCategoriesForBackup(): List<Category> = allCategories.value

    /**
     * Restores categories from a backup as new rows, returning a map from each
     * category's old (backed-up) id to its newly-generated one, so restored notes'
     * categoryId can be remapped onto it instead of pointing at an id that either
     * doesn't exist anymore or - worse - now belongs to an unrelated local category.
     */
    suspend fun restoreCategories(categories: List<Category>): Map<Int, Int> {
        val idMap = mutableMapOf<Int, Int>()
        for (category in categories) {
            try {
                val newId = categoryDao.insert(category.copy(id = 0)).toInt()
                idMap[category.id] = newId
            } catch (e: Exception) {
                // Skip this one category rather than aborting the whole restore
            }
        }
        return idMap
    }

    /**
     * Wipes all local notes and categories before a "Replace" restore, so the backup
     * becomes the sole source of truth instead of merging on top of what's here.
     */
    suspend fun deleteAllNotesAndCategories() {
        noteDao.deleteAllNotes()
        categoryDao.deleteAllCategories()
    }

    /**
     * Signature used to detect a note from a backup that's already present locally
     * (e.g. restoring the same backup twice, or restoring over data that was never
     * cleared), so "Merge" restores don't create duplicate copies of every note.
     */
    private fun noteSignature(note: Note) = Triple(note.title, note.content, note.createdAt)

    /**
     * Filters a batch of notes from a backup down to only the ones not already
     * present locally (by title+content+createdAt), for a "Merge" restore.
     */
    suspend fun filterOutExistingNotes(notes: List<Note>): List<Note> {
        val existingSignatures = noteDao.getAllNotes().first().map { noteSignature(it) }.toSet()
        return notes.filter { noteSignature(it) !in existingSignatures }
    }

    /**
     * Add restored note from backup. categoryIdMap remaps the note's old categoryId
     * (from the backup) onto the newly-restored category via [restoreCategories];
     * an id with no entry (backup predates category restore, or the category failed
     * to restore) falls back to no group instead of an unrelated local category.
     * Returns whether the insert succeeded, so callers can surface partial failures
     * instead of silently dropping notes.
     */
    suspend fun addRestoredNote(note: Note, categoryIdMap: Map<Int, Int> = emptyMap()): Boolean {
        return try {
            val restoredNote = note.copy(
                id = 0, // Room will auto-generate a new ID
                categoryId = note.categoryId?.let { categoryIdMap[it] },
                lastModifiedAt = System.currentTimeMillis()
            )
            noteDao.insertOrUpdate(restoredNote)
            true
        } catch (e: Exception) {
            false
        }
    }
}
