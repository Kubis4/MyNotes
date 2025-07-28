package sk.kubdev.selfnote.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.kubdev.selfnote.MainActivity
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.data.remote.local.NoteDatabase
import java.util.concurrent.TimeUnit
import java.util.Calendar

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            println("🔧 DEBUG: BackupWorker started")

            // Show progress notification
            showBackupNotification(
                context = applicationContext,
                title = applicationContext.getString(R.string.backup_notification_backing_up),
                message = applicationContext.getString(R.string.backup_notification_in_progress),
                isOngoing = true,
                showProgress = true
            )

            val googleDriveManager = GoogleDriveManager(applicationContext)

            // Check if user is signed in
            if (!googleDriveManager.isSignedIn()) {
                println("🔧 DEBUG: User not signed in to Google Drive, skipping backup")
                showBackupNotification(
                    context = applicationContext,
                    title = applicationContext.getString(R.string.backup_notification_skipped),
                    message = applicationContext.getString(R.string.backup_notification_sign_in_required),
                    isOngoing = false
                )
                return@withContext Result.success()
            }

            // Get database instance
            val database = NoteDatabase.getDatabase(applicationContext)
            val noteDao = database.noteDao()

            // Get all notes for backup
            val notes = noteDao.getAllNotesForBackup()

            println("🔧 DEBUG: Found ${notes.size} notes for automatic backup")

            // Update notification
            showBackupNotification(
                context = applicationContext,
                title = applicationContext.getString(R.string.backup_notification_backing_up),
                message = applicationContext.getString(R.string.backup_notification_backing_up_count, notes.size),
                isOngoing = true,
                showProgress = true
            )

            // Create backup with "Auto" prefix
            val backupResult = googleDriveManager.createBackup(
                notes = notes,
                includeArchived = true,
                customName = "Auto",
                onProgress = { progress ->
                    println("🔧 DEBUG: Auto-backup progress: $progress")
                    showBackupNotification(
                        context = applicationContext,
                        title = applicationContext.getString(R.string.backup_notification_backing_up),
                        message = progress,
                        isOngoing = true,
                        showProgress = true
                    )
                }
            )

            if (backupResult.isSuccess) {
                println("🔧 DEBUG: Automatic backup successful: ${backupResult.getOrNull()}")

                // Clean up old backups (keep last 10)
                googleDriveManager.cleanupOldBackups(keepLastN = 10)

                // Show success notification
                showBackupNotification(
                    context = applicationContext,
                    title = applicationContext.getString(R.string.backup_notification_success),
                    message = applicationContext.getString(R.string.backup_notification_success_message, notes.size),
                    isOngoing = false
                )

                Result.success()
            } else {
                println("🔧 DEBUG: Automatic backup failed: ${backupResult.exceptionOrNull()}")

                showBackupNotification(
                    context = applicationContext,
                    title = applicationContext.getString(R.string.backup_notification_failed),
                    message = applicationContext.getString(R.string.backup_notification_failed_message, backupResult.exceptionOrNull()?.message ?: ""),
                    isOngoing = false
                )

                Result.failure()
            }

        } catch (e: Exception) {
            println("🔧 DEBUG: BackupWorker error: ${e.message}")
            e.printStackTrace()

            showBackupNotification(
                context = applicationContext,
                title = applicationContext.getString(R.string.backup_notification_error),
                message = applicationContext.getString(R.string.backup_notification_error_message, e.message ?: ""),
                isOngoing = false
            )

            Result.failure()
        }
    }

    private fun showBackupNotification(
        context: Context,
        title: String,
        message: String,
        isOngoing: Boolean = false,
        showProgress: Boolean = false
    ) {
        try {
            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.backup_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.backup_notification_channel_description)
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Create intent to open app when notification is clicked
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isOngoing)
                .setAutoCancel(!isOngoing)
                .setContentIntent(pendingIntent)

            if (showProgress && isOngoing) {
                notificationBuilder.setProgress(0, 0, true)
            }

            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        } catch (e: Exception) {
            println("🔧 DEBUG: Failed to show notification: ${e.message}")
        }
    }

    companion object {
        const val WORK_NAME = "periodic_backup"
        const val BACKUP_WORK_TAG = "backup_work_tag"
        private const val NOTIFICATION_CHANNEL_ID = "backup_channel"
        private const val NOTIFICATION_ID = 1001

        fun schedulePeriodicBackup(
            context: Context,
            frequency: BackupFrequency,
            hour: Int,
            minute: Int,
            dayOfWeek: Int = Calendar.MONDAY,
            dayOfMonth: Int = 1
        ) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                when (frequency) {
                    BackupFrequency.DAILY -> {
                        // If time has passed today, schedule for tomorrow
                        if (before(now)) {
                            add(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                    BackupFrequency.WEEKLY -> {
                        set(Calendar.DAY_OF_WEEK, dayOfWeek)
                        // If day/time has passed this week, schedule for next week
                        if (before(now)) {
                            add(Calendar.WEEK_OF_YEAR, 1)
                        }
                    }
                    BackupFrequency.MONTHLY -> {
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        // If day/time has passed this month, schedule for next month
                        if (before(now)) {
                            add(Calendar.MONTH, 1)
                        }
                    }
                }
            }

            val initialDelay = target.timeInMillis - now.timeInMillis

            val repeatInterval = when (frequency) {
                BackupFrequency.DAILY -> 24L to TimeUnit.HOURS
                BackupFrequency.WEEKLY -> 7L to TimeUnit.DAYS
                BackupFrequency.MONTHLY -> 30L to TimeUnit.DAYS
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = repeatInterval.first,
                repeatIntervalTimeUnit = repeatInterval.second
            )
                .setConstraints(constraints)
                .addTag(BACKUP_WORK_TAG)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                backupRequest
            )

            println("🔧 DEBUG: Scheduled $frequency backup at ${String.format("%02d:%02d", hour, minute)}")
        }

        fun cancelPeriodicBackup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            println("🔧 DEBUG: Cancelled periodic backup")
        }

        fun scheduleOneTimeBackup(context: Context, delayMinutes: Long = 0) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val backupRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(BACKUP_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueue(backupRequest)

            println("🔧 DEBUG: Scheduled one-time backup in $delayMinutes minutes")
        }
    }
}
