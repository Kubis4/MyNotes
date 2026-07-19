// Uses the deprecated GoogleSignInAccount type for Drive backup account handling.
// Deprecated but functional; see GoogleDriveManager for the migration note.
@file:Suppress("DEPRECATION")

package sk.kubdev.mynotes.backup

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.kubdev.mynotes.NoteViewModel
import sk.kubdev.mynotes.data.remote.local.entities.Note
import sk.kubdev.mynotes.R
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.util.Calendar

class BackupViewModel(
    private val context: Context,
    private val noteViewModel: NoteViewModel
) : ViewModel() {

    private val googleDriveManager = GoogleDriveManager(context)
    private val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _backupFiles = MutableStateFlow<List<BackupFile>>(emptyList())
    val backupFiles: StateFlow<List<BackupFile>> = _backupFiles.asStateFlow()

    init {
        checkSignInStatus()
        viewModelScope.launch {
            delay(500)
            if (_uiState.value.isSignedIn) {
                loadBackupFiles()
            }
        }
    }

    private fun checkSignInStatus() {
        _uiState.value = _uiState.value.copy(
            isSignedIn = googleDriveManager.isSignedIn(),
            accountInfo = googleDriveManager.getAccountInfo()
        )
    }

    fun getSignInIntent(): Intent {
        return googleDriveManager.getSignInIntent()
    }

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val result = googleDriveManager.handleSignInResult(data)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isSignedIn = true,
                        accountInfo = googleDriveManager.getAccountInfo(),
                        isLoading = false,
                        message = result.getOrNull()
                    )
                    loadBackupFiles()
                } else {
                    val error = result.exceptionOrNull()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.backup_sign_in_failed, error?.message ?: "")
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = context.getString(R.string.backup_sign_in_error, e.message ?: "")
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = googleDriveManager.signOut()

            _uiState.value = _uiState.value.copy(
                isSignedIn = false,
                accountInfo = null,
                isLoading = false,
                message = result.getOrNull()
            )

            _backupFiles.value = emptyList()
            setAutoBackupEnabled(false)
        }
    }

    fun createBackup(includeArchived: Boolean = true, customName: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCreatingBackup = true,
                backupProgress = context.getString(R.string.backup_starting)
            )

            try {
                val notes = noteViewModel.getAllNotesForBackup()
                val categories = noteViewModel.getAllCategoriesForBackup()

                val result = googleDriveManager.createBackup(
                    notes = notes,
                    categories = categories,
                    includeArchived = includeArchived,
                    customName = customName,
                    onProgress = { progress ->
                        _uiState.value = _uiState.value.copy(backupProgress = progress)
                    }
                )

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isCreatingBackup = false,
                        backupProgress = "",
                        message = result.getOrNull()
                    )
                    loadBackupFiles()
                    BackupNotifier.show(
                        context = context,
                        title = context.getString(R.string.backup_notification_success),
                        message = context.getString(R.string.backup_notification_success_message, notes.size),
                        isOngoing = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCreatingBackup = false,
                        backupProgress = "",
                        error = context.getString(R.string.backup_failed, result.exceptionOrNull()?.message ?: "")
                    )
                    BackupNotifier.show(
                        context = context,
                        title = context.getString(R.string.backup_notification_failed),
                        message = context.getString(R.string.backup_notification_failed_message, result.exceptionOrNull()?.message ?: ""),
                        isOngoing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingBackup = false,
                    backupProgress = "",
                    error = context.getString(R.string.backup_error, e.message ?: "")
                )
                BackupNotifier.show(
                    context = context,
                    title = context.getString(R.string.backup_notification_error),
                    message = context.getString(R.string.backup_notification_error_message, e.message ?: ""),
                    isOngoing = false
                )
            }
        }
    }

    fun loadBackupFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBackups = true)

            val result = googleDriveManager.listBackups()

            if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()
                _backupFiles.value = files
                _uiState.value = _uiState.value.copy(isLoadingBackups = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoadingBackups = false,
                    error = context.getString(R.string.backup_load_failed, result.exceptionOrNull()?.message ?: "")
                )
            }
        }
    }

    /**
     * @param replaceExisting Replace mode wipes all local notes/categories first so the
     * backup becomes the sole source of truth. Merge mode (default) keeps existing data
     * and skips any backed-up note that already exists locally (by title+content+createdAt),
     * so restoring the same backup twice - or restoring on top of data that was never
     * cleared - doesn't create duplicate copies of every note.
     */
    fun restoreFromBackup(backupFileId: String, replaceExisting: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRestoring = true,
                restoreProgress = context.getString(R.string.backup_restore_starting)
            )

            val result = googleDriveManager.restoreFromBackup(
                backupFileId = backupFileId,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(restoreProgress = progress)
                }
            )

            if (result.isSuccess) {
                val restoreResult = result.getOrNull()!!

                if (replaceExisting) {
                    noteViewModel.deleteAllNotesAndCategories()
                }

                // Categories/groups must land first so notes can be remapped onto
                // their newly-generated ids instead of stale ones from the backup.
                val categoryIdMap = noteViewModel.restoreCategories(restoreResult.categories)

                val notesToRestore = if (replaceExisting) {
                    restoreResult.notes
                } else {
                    noteViewModel.filterOutExistingNotes(restoreResult.notes)
                }
                val skippedDuplicates = restoreResult.notes.size - notesToRestore.size

                var restoredCount = 0
                notesToRestore.forEach { note: Note ->
                    if (noteViewModel.addRestoredNote(note, categoryIdMap)) {
                        restoredCount++
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    restoreProgress = "",
                    message = when {
                        skippedDuplicates > 0 ->
                            context.getString(
                                R.string.backup_restore_success_with_duplicates,
                                restoredCount,
                                skippedDuplicates
                            )
                        restoredCount == restoreResult.notes.size ->
                            context.getString(R.string.backup_restore_success, restoredCount)
                        else ->
                            context.getString(
                                R.string.backup_restore_partial,
                                restoredCount,
                                restoreResult.notes.size
                            )
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    restoreProgress = "",
                    error = context.getString(R.string.backup_restore_failed, result.exceptionOrNull()?.message ?: "")
                )
            }
        }
    }

    fun deleteBackup(backupFileId: String) {
        viewModelScope.launch {
            val result = googleDriveManager.deleteBackup(backupFileId)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(message = result.getOrNull())
                loadBackupFiles()
            } else {
                _uiState.value = _uiState.value.copy(
                    error = context.getString(R.string.backup_delete_failed, result.exceptionOrNull()?.message ?: "")
                )
            }
        }
    }

    fun isAutoBackupEnabled(): Boolean {
        return prefs.getBoolean("auto_backup_enabled", false)
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()

        if (enabled) {
            scheduleAutoBackup()
        } else {
            BackupWorker.cancelPeriodicBackup(context)
            _uiState.value = _uiState.value.copy(
                message = context.getString(R.string.backup_auto_disabled)
            )
        }
    }

    fun getBackupSettings(): BackupSettings {
        val frequency = BackupFrequency.valueOf(
            prefs.getString("backup_frequency", BackupFrequency.DAILY.name)
                ?: BackupFrequency.DAILY.name
        )
        val hour = prefs.getInt("backup_hour", 2)
        val minute = prefs.getInt("backup_minute", 0)
        val dayOfWeek = prefs.getInt("backup_day_of_week", Calendar.MONDAY)
        val dayOfMonth = prefs.getInt("backup_day_of_month", 1)

        return BackupSettings(frequency, hour, minute, dayOfWeek, dayOfMonth)
    }

    fun updateBackupSettings(settings: BackupSettings) {
        prefs.edit().apply {
            putString("backup_frequency", settings.frequency.name)
            putInt("backup_hour", settings.hour)
            putInt("backup_minute", settings.minute)
            putInt("backup_day_of_week", settings.dayOfWeek)
            putInt("backup_day_of_month", settings.dayOfMonth)
            apply()
        }

        if (isAutoBackupEnabled()) {
            scheduleAutoBackup()
        }
    }

    private fun scheduleAutoBackup() {
        val settings = getBackupSettings()
        BackupWorker.schedulePeriodicBackup(
            context = context,
            frequency = settings.frequency,
            hour = settings.hour,
            minute = settings.minute,
            dayOfWeek = settings.dayOfWeek,
            dayOfMonth = settings.dayOfMonth
        )

        val frequencyText = when (settings.frequency) {
            BackupFrequency.DAILY -> context.getString(R.string.backup_frequency_daily)
            BackupFrequency.WEEKLY -> context.getString(R.string.backup_frequency_weekly, getDayName(settings.dayOfWeek))
            BackupFrequency.MONTHLY -> context.getString(R.string.backup_frequency_monthly, settings.dayOfMonth)
        }

        _uiState.value = _uiState.value.copy(
            message = context.getString(
                R.string.backup_auto_enabled,
                frequencyText,
                String.format("%02d:%02d", settings.hour, settings.minute)
            )
        )
    }

    private fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.SUNDAY -> context.getString(R.string.day_sunday)
            Calendar.MONDAY -> context.getString(R.string.day_monday)
            Calendar.TUESDAY -> context.getString(R.string.day_tuesday)
            Calendar.WEDNESDAY -> context.getString(R.string.day_wednesday)
            Calendar.THURSDAY -> context.getString(R.string.day_thursday)
            Calendar.FRIDAY -> context.getString(R.string.day_friday)
            Calendar.SATURDAY -> context.getString(R.string.day_saturday)
            else -> context.getString(R.string.day_monday)
        }
    }

    fun triggerImmediateBackup() {
        BackupWorker.scheduleOneTimeBackup(context, delayMinutes = 0)
        _uiState.value = _uiState.value.copy(
            message = context.getString(R.string.backup_manual_scheduled)
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class BackupUiState(
    val isSignedIn: Boolean = false,
    val accountInfo: GoogleSignInAccount? = null,
    val isLoading: Boolean = false,
    val isCreatingBackup: Boolean = false,
    val isLoadingBackups: Boolean = false,
    val isRestoring: Boolean = false,
    val backupProgress: String = "",
    val restoreProgress: String = "",
    val message: String? = null,
    val error: String? = null
)
