package sk.kubisdev.mynotes.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules and cancels one-shot exact alarms for note reminders (Google Keep style).
 *
 * Each note gets a single pending alarm keyed by its id (used as the PendingIntent
 * request code), so scheduling a new reminder for the same note replaces the old one and
 * [cancel] can target it precisely. When the alarm fires, [ReminderReceiver] posts the
 * notification.
 *
 * Exact vs inexact: on Android 12+ (S) exact alarms need the user-revocable
 * SCHEDULE_EXACT_ALARM permission. We check [AlarmManager.canScheduleExactAlarms] and fall
 * back to an inexact allow-while-idle alarm when it's not granted, so a reminder still
 * arrives (just possibly a little late) instead of silently never firing.
 */
object ReminderScheduler {

    const val EXTRA_NOTE_ID = "reminder_note_id"
    const val EXTRA_NOTE_TITLE = "reminder_note_title"
    const val EXTRA_NOTE_TYPE = "reminder_note_type"

    private const val ACTION_REMINDER = "sk.kubisdev.mynotes.action.REMINDER"

    /**
     * @return true if an exact alarm was scheduled, false if it fell back to inexact
     * (because the app can't schedule exact alarms). Callers can use this to hint the
     * user to grant the permission.
     */
    fun schedule(
        context: Context,
        noteId: Int,
        title: String,
        type: String,
        triggerAtMillis: Long
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // FLAG_UPDATE_CURRENT (the non-cancel path) never returns null; the elvis is only
        // to satisfy the nullable platform type.
        val pendingIntent = buildPendingIntent(context, noteId, title, type, forCancel = false)
            ?: return false

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        return try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
                true
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
                false
            }
        } catch (e: SecurityException) {
            // Defensive: some OEMs throw even when canScheduleExactAlarms() said yes.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
            false
        }
    }

    fun cancel(context: Context, noteId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, noteId, "", "", forCancel = true)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    // For cancel we only need to match an existing PendingIntent (same request code +
    // component + action), so extras are irrelevant and FLAG_NO_CREATE avoids allocating
    // one that never existed. For scheduling we always (re)create with UPDATE_CURRENT so
    // the freshest title/type ride along.
    private fun buildPendingIntent(
        context: Context,
        noteId: Int,
        title: String,
        type: String,
        forCancel: Boolean
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_NOTE_TITLE, title)
            putExtra(EXTRA_NOTE_TYPE, type)
        }
        val flags = if (forCancel) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, noteId, intent, flags)
    }
}
