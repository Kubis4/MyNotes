package sk.kubdev.mynotes.collab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import sk.kubdev.mynotes.MainActivity
import sk.kubdev.mynotes.R
import java.util.concurrent.TimeUnit

// Polls for pending collaboration invites in the background so the recipient gets
// a system notification even with the app closed / device locked. FCM push would
// need a Cloud Functions backend (Blaze plan); a 15-minute WorkManager poll costs
// ~100 Firestore reads per day per device, well inside the free tier, and reuses
// the exact query the in-app pending-invites listener runs.
class InviteCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val email = user.email ?: return Result.success()

        val snapshot = try {
            FirebaseFirestore.getInstance()
                .collection("note_invites")
                .whereEqualTo("recipientEmail", email)
                .whereEqualTo("status", "PENDING")
                .get()
                .await()
        } catch (e: Exception) {
            // Transient network/auth hiccup - try again at the next periodic run.
            return Result.success()
        }

        data class PendingInvite(val id: String, val sender: String, val noteTitle: String)

        val pending = snapshot.documents.map { doc ->
            PendingInvite(
                id = doc.id,
                sender = doc.getString("senderDisplayName")
                    ?: doc.getString("senderEmail")
                    ?: "",
                noteTitle = doc.getString("noteTitle") ?: ""
            )
        }

        // Only notify once per invite. The stored set is rewritten to the currently
        // pending ids each run, so it can't grow unboundedly and an invite that was
        // declined and re-sent notifies again.
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyNotified = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()) ?: emptySet()
        val newInvites = pending.filter { it.id !in alreadyNotified }

        newInvites.forEach { invite ->
            showNotification(invite.id, invite.sender, invite.noteTitle)
        }

        prefs.edit().putStringSet(KEY_NOTIFIED_IDS, pending.map { it.id }.toSet()).apply()
        return Result.success()
    }

    private fun showNotification(inviteId: String, sender: String, noteTitle: String) {
        val context = applicationContext
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.nav_invitations),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Same launch flags as BackupNotifier: fire from a non-Activity context and
        // front an already-running instance instead of stacking a new one.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            inviteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.nav_invitations))
            .setContentText(context.getString(R.string.collab_new_invite, sender, noteTitle))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // One notification per invite (id-derived), so multiple invites all show.
        notificationManager.notify(inviteId.hashCode(), notification)
    }

    companion object {
        private const val WORK_NAME = "invite_check"
        private const val CHANNEL_ID = "invite_channel"
        private const val PREFS_NAME = "invite_notifications"
        private const val KEY_NOTIFIED_IDS = "notified_ids"

        // KEEP: rescheduling on every app start must not reset the 15-minute cycle.
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InviteCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
