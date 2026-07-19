// Uses the deprecated GoogleSignIn / GoogleSignInOptions / AndroidHttp APIs for Google
// Drive backup. These are deprecated but fully functional; a migration to Credential
// Manager + AuthorizationClient is recommended as a separate, device-tested change.
@file:Suppress("DEPRECATION")

package sk.kubdev.mynotes.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import sk.kubdev.mynotes.data.remote.local.entities.Category
import sk.kubdev.mynotes.data.remote.local.entities.Note
import sk.kubdev.mynotes.R
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class GoogleDriveManager(private val context: Context) {

    private var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null
    private val gson = Gson()

    companion object {
        private const val FOLDER_NAME = "MyNotes - Backup"
        private const val BACKUP_MIME_TYPE = "application/json"
    }

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            // ID token lets the Drive sign-in double as the Firebase (collaboration)
            // sign-in - one account, one prompt, both features unlocked.
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)

        // 🔧 FIX: Initialize Drive service if already signed in
        initializeDriveServiceIfSignedIn()
    }

    // 🔧 NEW: Initialize Drive service on startup if already signed in
    private fun initializeDriveServiceIfSignedIn() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            initializeDriveService(account)
            println("🔧 DEBUG: Drive service initialized on startup")
        }
    }

    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val isSignedIn = account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))

        // 🔧 FIX: Ensure Drive service is initialized when checking sign-in status
        if (isSignedIn && driveService == null) {
            initializeDriveService(account!!)
        }

        return isSignedIn
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun handleSignInResult(data: Intent?): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (data == null) {
                return@withContext Result.failure(Exception(context.getString(R.string.drive_sign_in_cancelled)))
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result

            println("🔧 DEBUG: Account: ${account?.email}")
            println("🔧 DEBUG: Granted scopes: ${account?.grantedScopes}")

            if (account == null) {
                return@withContext Result.failure(Exception(context.getString(R.string.drive_failed_get_account)))
            }

            initializeDriveService(account)

            if (driveService != null) {
                println("🔧 DEBUG: Drive service initialized successfully")
            } else {
                println("🔧 DEBUG: Failed to initialize Drive service!")
            }

            // Unified sign-in: the same Google account also signs into Firebase, so
            // collaboration works immediately without a second sign-in prompt.
            try {
                val idToken = account.idToken
                val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
                if (idToken != null && firebaseAuth.currentUser == null) {
                    val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                    firebaseAuth.signInWithCredential(credential).await()
                    println("🔧 DEBUG: Firebase signed in via Drive account")
                }
            } catch (e: Exception) {
                // Collaboration sign-in is best-effort here; Drive backup still works
                println("🔧 DEBUG: Firebase sign-in via Drive failed: ${e.message}")
            }

            Result.success(context.getString(R.string.drive_sign_in_success, account.email))

        } catch (e: Exception) {
            println("🔧 DEBUG: Exception type: ${e.javaClass.simpleName}")
            println("🔧 DEBUG: Exception message: ${e.message}")
            Result.failure(Exception(context.getString(R.string.drive_sign_in_failed, e.message ?: "")))
        }
    }

    private fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory(),
            credential
        )
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }

    suspend fun signOut(): Result<String> = withContext(Dispatchers.IO) {
        try {
            googleSignInClient.signOut().await()
            driveService = null
            Result.success(context.getString(R.string.drive_sign_out_success))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBackup(
        notes: List<Note>,
        categories: List<Category> = emptyList(),
        includeArchived: Boolean = true,
        customName: String = "",
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            println("🔧 DEBUG: GoogleDriveManager.createBackup started")

            val drive = driveService
            if (drive == null) {
                println("🔧 DEBUG: Drive service is null!")
                return@withContext Result.failure(
                    Exception(context.getString(R.string.drive_not_signed_in))
                )
            }
            println("🔧 DEBUG: Drive service is initialized")

            onProgress(context.getString(R.string.drive_preparing_backup))

            val notesToBackup = if (includeArchived) {
                notes.filter { !it.isDeleted }
            } else {
                notes.filter { !it.isDeleted && !it.isArchived }
            }

            println("🔧 DEBUG: Backing up ${notesToBackup.size} notes")

            val backupData = BackupData(
                version = "1.0",
                timestamp = System.currentTimeMillis(),
                totalNotes = notesToBackup.size,
                notes = notesToBackup,
                categories = categories
            )

            onProgress(context.getString(R.string.drive_creating_backup_file))

            val jsonData = gson.toJson(backupData)
            val content = jsonData.toByteArray()

            println("🔧 DEBUG: JSON data size: ${content.size} bytes")

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(Date())

            // Create filename with custom name if provided
            val fileName = if (customName.isNotEmpty()) {
                "MyNotes_${customName}_$timestamp.json"
            } else {
                "MyNotes_Backup_$timestamp.json"
            }

            onProgress(context.getString(R.string.drive_uploading_to_drive))

            println("🔧 DEBUG: Getting or creating folder...")
            val folderId = getOrCreateFolder(drive)
            println("🔧 DEBUG: Folder ID: $folderId")

            val fileMetadata = File().apply {
                name = fileName
                parents = listOf(folderId)
                mimeType = BACKUP_MIME_TYPE
            }

            val mediaContent = ByteArrayContent(
                BACKUP_MIME_TYPE,
                content
            )

            println("🔧 DEBUG: Uploading file to Drive...")
            val file = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, size, createdTime")
                .execute()

            println("🔧 DEBUG: File uploaded successfully: ${file.name} (ID: ${file.id})")

            onProgress(context.getString(R.string.drive_backup_completed))

            Result.success(context.getString(R.string.drive_backup_created_success, file.name))

        } catch (e: Exception) {
            println("🔧 DEBUG: Exception in createBackup: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun listBackups(): Result<List<BackupFile>> = withContext(Dispatchers.IO) {
        try {
            // 🔧 FIX: Ensure Drive service is initialized
            if (driveService == null) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    initializeDriveService(account)
                } else {
                    return@withContext Result.failure(Exception(context.getString(R.string.drive_not_signed_in)))
                }
            }

            val drive = driveService ?: return@withContext Result.failure(
                Exception(context.getString(R.string.drive_service_not_initialized))
            )

            println("🔧 DEBUG: Listing backups...")

            val folderId = getOrCreateFolder(drive)

            val result: FileList = drive.files().list()
                .setQ("'$folderId' in parents and mimeType='$BACKUP_MIME_TYPE' and trashed=false")
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, size, createdTime, modifiedTime)")
                .execute()

            val backupFiles = result.files.map { file ->
                BackupFile(
                    id = file.id,
                    name = file.name,
                    size = file.size.toLong(),
                    createdTime = file.createdTime?.value ?: 0,
                    modifiedTime = file.modifiedTime?.value ?: 0,
                    context = context
                )
            }

            println("🔧 DEBUG: Found ${backupFiles.size} backup files")

            Result.success(backupFiles)

        } catch (e: Exception) {
            println("🔧 DEBUG: Error listing backups: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackup(
        backupFileId: String,
        onProgress: (String) -> Unit = {}
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                Exception(context.getString(R.string.drive_not_signed_in))
            )

            onProgress(context.getString(R.string.drive_downloading_backup))

            val outputStream = ByteArrayOutputStream()
            drive.files().get(backupFileId).executeMediaAndDownloadTo(outputStream)
            val jsonData = outputStream.toString("UTF-8")

            onProgress(context.getString(R.string.drive_processing_backup))

            val backupData = gson.fromJson(jsonData, BackupData::class.java)

            onProgress(context.getString(R.string.drive_restore_completed))

            val result = RestoreResult(
                totalNotes = backupData.totalNotes,
                notes = backupData.notes,
                // Older backups predate the categories field; Gson leaves it null
                // rather than applying the Kotlin default, so fall back explicitly.
                categories = backupData.categories ?: emptyList(),
                backupVersion = backupData.version,
                backupTimestamp = backupData.timestamp
            )

            Result.success(result)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(backupFileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                Exception(context.getString(R.string.drive_not_signed_in))
            )

            drive.files().delete(backupFileId).execute()
            Result.success(context.getString(R.string.drive_backup_deleted_success))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getOrCreateFolder(drive: Drive): String {
        try {
            println("🔧 DEBUG: Searching for existing folder...")
            val result: FileList = drive.files().list()
                .setQ("name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
                .setFields("files(id, name)")
                .execute()

            return if (result.files.isNotEmpty()) {
                println("🔧 DEBUG: Found existing folder: ${result.files[0].id}")
                result.files[0].id
            } else {
                println("🔧 DEBUG: Creating new folder...")
                val folderMetadata = File().apply {
                    name = FOLDER_NAME
                    mimeType = "application/vnd.google-apps.folder"
                }

                val folder = drive.files().create(folderMetadata)
                    .setFields("id")
                    .execute()

                println("🔧 DEBUG: Created new folder: ${folder.id}")
                folder.id
            }
        } catch (e: Exception) {
            println("🔧 DEBUG: Error in getOrCreateFolder: ${e.message}")
            throw e
        }
    }

    fun getAccountInfo(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun cleanupOldBackups(keepLastN: Int = 10): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backupsResult = listBackups()
            if (backupsResult.isSuccess) {
                val files = backupsResult.getOrNull() ?: return@withContext Result.success(context.getString(R.string.drive_no_backups_to_clean))
                if (files.size > keepLastN) {
                    val filesToDelete = files.drop(keepLastN)
                    filesToDelete.forEach { file ->
                        deleteBackup(file.id)
                    }
                    Result.success(context.getString(R.string.drive_deleted_old_backups, filesToDelete.size))
                } else {
                    Result.success(context.getString(R.string.drive_no_old_backups))
                }
            } else {
                Result.failure(Exception(context.getString(R.string.drive_failed_list_backups)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class BackupData(
    val version: String,
    val timestamp: Long,
    val totalNotes: Int,
    val notes: List<Note>,
    // Nullable (not defaulted to emptyList) because Gson populates fields via
    // reflection when deserializing, bypassing the Kotlin default entirely for
    // backups created before this field existed - so it must tolerate null.
    val categories: List<Category>? = null
)

data class BackupFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: Long,
    val modifiedTime: Long,
    val context: Context? = null
) {
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> context?.getString(R.string.file_size_bytes, size) ?: "$size B"
            size < 1024 * 1024 -> context?.getString(R.string.file_size_kb, size / 1024) ?: "${size / 1024} KB"
            else -> context?.getString(R.string.file_size_mb, size / (1024 * 1024)) ?: "${size / (1024 * 1024)} MB"
        }
    }

    fun getFormattedDate(): String {
        val pattern = context?.getString(R.string.backup_date_format) ?: "MMM dd, yyyy HH:mm"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(createdTime))
    }
}

data class RestoreResult(
    val totalNotes: Int,
    val notes: List<Note>,
    val categories: List<Category> = emptyList(),
    val backupVersion: String,
    val backupTimestamp: Long
)
