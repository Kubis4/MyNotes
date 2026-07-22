package sk.kubisdev.mynotes.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import sk.kubisdev.mynotes.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Google Keep-style reminder picker: a few one-tap quick options, a custom date & time
 * path, plus a "remove" affordance when a reminder is already set.
 *
 * @param currentReminder existing reminder epoch millis, or null if none
 * @param onSet called with the chosen epoch millis (already validated to be in the future)
 * @param onRemove called when the user removes the existing reminder
 * @param onDismiss called to close the dialog without changes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    currentReminder: Long?,
    onSet: (Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateTimeFormatter = remember {
        SimpleDateFormat("EEE, d MMM • HH:mm", Locale.getDefault())
    }

    // --- Main options dialog ---
    if (!showDatePicker && !showTimePicker) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 20.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.reminder_dialog_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    if (currentReminder != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.reminder_current,
                                dateTimeFormatter.format(Date(currentReminder))
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    ReminderOptionRow(
                        icon = Icons.Default.WbTwilight,
                        label = stringResource(R.string.reminder_option_later_today),
                        trailing = remember { formatTime(laterToday()) }
                    ) { onSet(laterToday()) }

                    ReminderOptionRow(
                        icon = Icons.Default.WbSunny,
                        label = stringResource(R.string.reminder_option_tomorrow),
                        trailing = remember { formatTime(tomorrowMorning()) }
                    ) { onSet(tomorrowMorning()) }

                    ReminderOptionRow(
                        icon = Icons.Default.DateRange,
                        label = stringResource(R.string.reminder_option_next_week),
                        trailing = remember { formatDayTime(nextWeek()) }
                    ) { onSet(nextWeek()) }

                    ReminderOptionRow(
                        icon = Icons.Default.CalendarMonth,
                        label = stringResource(R.string.reminder_option_pick),
                        trailing = null
                    ) { showDatePicker = true }

                    if (currentReminder != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ReminderOptionRow(
                            icon = Icons.Default.DeleteOutline,
                            label = stringResource(R.string.reminder_remove),
                            trailing = null,
                            tint = MaterialTheme.colorScheme.error
                        ) { onRemove() }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
    }

    // --- Custom date picker ---
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentReminder ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringResource(R.string.action_set)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // --- Custom time picker ---
    if (showTimePicker) {
        val initial = remember {
            Calendar.getInstance().apply {
                timeInMillis = currentReminder ?: System.currentTimeMillis()
            }
        }
        val timeState = rememberTimePickerState(
            initialHour = initial.get(Calendar.HOUR_OF_DAY),
            initialMinute = initial.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = pickedDateMillis ?: System.currentTimeMillis()
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = dateMillis
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    showTimePicker = false
                    onSet(cal.timeInMillis)
                }) { Text(stringResource(R.string.action_set)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            }
        )
    }
}

/**
 * A short, localized "reminder in …" toast string for a chosen reminder time, e.g.
 * "Reminder in 2 h 30 min". Falls back to a "moment" phrasing for sub-minute leads.
 * Unit abbreviations (d / h / min) are kept literal - they read the same across the
 * app's languages.
 */
fun reminderLeadToastText(context: android.content.Context, timeMillis: Long): String {
    var remaining = timeMillis - System.currentTimeMillis()
    if (remaining < 0) remaining = 0
    val totalMinutes = remaining / 60000L
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60

    val duration = when {
        days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
        hours > 0 -> if (minutes > 0) "$hours h $minutes min" else "$hours h"
        minutes > 0 -> "$minutes min"
        else -> "< 1 min"
    }
    return context.getString(R.string.reminder_toast_in, duration)
}

/**
 * Returns a lambda that requests the POST_NOTIFICATIONS runtime permission (Android 13+)
 * if it isn't already granted - reminders are useless without it. No-op on older versions
 * or when already granted. Reused by every screen that offers the reminder picker.
 */
@Composable
fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* ignored: reminder is still stored; the notification just won't post */ }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun ReminderOptionRow(
    icon: ImageVector,
    label: String,
    trailing: String?,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.75f))
        Spacer(Modifier.width(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// --- Quick-option time helpers ---

// ~3 hours from now, snapped up to the next whole hour, so "Later today" lands on a
// clean time rather than an odd minute.
private fun laterToday(): Long = Calendar.getInstance().apply {
    add(Calendar.HOUR_OF_DAY, 3)
    if (get(Calendar.MINUTE) > 0) add(Calendar.HOUR_OF_DAY, 1)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun tomorrowMorning(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 8)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun nextWeek(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 7)
    set(Calendar.HOUR_OF_DAY, 8)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatDayTime(millis: Long): String =
    SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(millis))
