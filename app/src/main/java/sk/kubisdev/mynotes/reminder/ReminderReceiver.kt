package sk.kubisdev.mynotes.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sk.kubisdev.mynotes.MainActivity
import sk.kubisdev.mynotes.R
import sk.kubisdev.mynotes.data.remote.local.NoteDatabase

/**
 * Fires when a note reminder alarm goes off: posts a notification that opens the note,
 * then clears the stored reminder so it doesn't linger in the UI or get re-armed on the
 * next reboot reschedule.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTE_ID, -1)
        if (noteId <= 0) return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TITLE) ?: ""
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TYPE) ?: "TEXT"

        showReminderNotification(context, noteId, title, type)

        // Clear the reminder in the DB off the main thread; goAsync keeps the receiver
        // alive until the update finishes.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NoteDatabase.getDatabase(context).noteDao().clearReminder(noteId)
            } catch (_: Exception) {
                // Best effort - the notification already went out, which is what matters.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "reminder_channel"
        private const val NOTIFICATION_TAG = "note_reminder"

        // Shared by the receiver (on-time) and BootReceiver (missed reminders after a
        // power-off), so both post an identical notification.
        fun showReminderNotification(context: Context, noteId: Int, title: String, type: String) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.reminder_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.reminder_channel_description)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                // Tapping opens the note directly. CLEAR_TOP + SINGLE_TOP reuse a running
                // instance (routed via MainActivity.onNewIntent) instead of stacking one.
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
                    putExtra(MainActivity.EXTRA_OPEN_NOTE_TYPE, type)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    noteId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val contentTitle = title.ifBlank { context.getString(R.string.reminder_default_title) }

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(contentTitle)
                    .setContentText(context.getString(R.string.reminder_notification_text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                // A per-note tag keeps reminder notifications from colliding with the
                // backup notification (which uses a fixed numeric id).
                notificationManager.notify(NOTIFICATION_TAG, noteId, builder.build())
            } catch (e: Exception) {
                println("🔧 DEBUG: Failed to show reminder notification: ${e.message}")
            }
        }
    }
}
