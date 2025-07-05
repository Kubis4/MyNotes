package sk.kubdev.selfnote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val noteDao = NoteDatabase.getDatabase(application).noteDao()
    private var autoSaveJob: Job? = null
    private val autoSaveDelay = 1500L // 1.5 seconds

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()
    private var recentlyDeletedNote: Note? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allNotes: StateFlow<List<Note>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                noteDao.getAllNotes()
            } else {
                noteDao.searchNotes("%$query%")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // --- FIX: Modified to accept NoteType ---
    private suspend fun performSave(id: Int, title: String, content: String, type: NoteType): Int {
        val currentTime = System.currentTimeMillis()
        val noteToSave = if (id != 0) {
            val existingNote = noteDao.getNoteById(id).firstOrNull()
            Note(
                id = id,
                title = title.ifBlank { if (type == NoteType.CHECKLIST) "Untitled To-Do" else "Untitled Note" },
                content = content,
                type = type, // <-- Pass the type
                createdAt = existingNote?.createdAt ?: currentTime,
                lastModifiedAt = currentTime
            )
        } else {
            Note(
                title = title.ifBlank { if (type == NoteType.CHECKLIST) "Untitled To-Do" else "Untitled Note" },
                content = content,
                type = type, // <-- Pass the type
                createdAt = currentTime,
                lastModifiedAt = currentTime
            )
        }
        return noteDao.insertOrUpdate(noteToSave).toInt()
    }

    // --- FIX: Modified to accept NoteType ---
    fun finaliseAndSave(noteId: Int, title: String, contentJson: String, noteType: NoteType): Job {
        autoSaveJob?.cancel()
        return viewModelScope.launch {
            if (noteId == 0 && title.isBlank() && (contentJson.isBlank() || contentJson == "[]")) {
                return@launch
            }
            performSave(noteId, title, contentJson, noteType)
        }
    }

    // --- FIX: Modified to accept NoteType ---
    fun triggerAutoSave(
        noteId: Int,
        title: String,
        lines: List<NoteLine>,
        noteType: NoteType, // <-- Add this parameter
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

    fun deleteNoteById(id: Int) = viewModelScope.launch {
        autoSaveJob?.cancel()
        noteDao.getNoteById(id).firstOrNull()?.let { noteToDelete ->
            recentlyDeletedNote = noteToDelete
            noteDao.delete(noteToDelete)
            _uiEvent.send(UiEvent.ShowUndoSnackbar)
        }
    }

    fun getNoteById(id: Int): Flow<Note?> {
        return noteDao.getNoteById(id)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun triggerDelete(note: Note) = viewModelScope.launch {
        recentlyDeletedNote = note
        noteDao.delete(note)
        _uiEvent.send(UiEvent.ShowUndoSnackbar)
    }



    fun undoDelete() = viewModelScope.launch {
        recentlyDeletedNote?.let {
            noteDao.insertOrUpdate(it)
            recentlyDeletedNote = null
        }
    }

    sealed class UiEvent {
        object ShowUndoSnackbar : UiEvent()
    }
}