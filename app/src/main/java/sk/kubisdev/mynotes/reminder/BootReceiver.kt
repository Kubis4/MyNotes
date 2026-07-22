package sk.kubisdev.mynotes.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sk.kubisdev.mynotes.data.remote.local.NoteDatabase

/**
 * Re-arms note reminders after a reboot or an app update - exact alarms don't survive
 * either. Future reminders are rescheduled; reminders whose time already passed while the
 * device was off are delivered immediately (once), so a missed reminder isn't lost.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val noteDao = NoteDatabase.getDatabase(context).noteDao()
                val notes = noteDao.getNotesWithReminders()
                val now = System.currentTimeMillis()

                notes.forEach { note ->
                    val at = note.reminderAt ?: return@forEach
                    if (at > now) {
                        ReminderScheduler.schedule(context, note.id, note.title, note.type.name, at)
                    } else {
                        // Missed while powered off - fire now and clear so it doesn't repeat.
                        ReminderReceiver.showReminderNotification(
                            context, note.id, note.title, note.type.name
                        )
                        noteDao.clearReminder(note.id)
                    }
                }
            } catch (_: Exception) {
                // Nothing actionable on boot; skip silently.
            } finally {
                pending.finish()
            }
        }
    }
}
