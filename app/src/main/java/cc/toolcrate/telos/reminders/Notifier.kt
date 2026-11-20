package cc.toolcrate.telos.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.toolcrate.telos.MainActivity
import cc.toolcrate.telos.R
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.ui.Format
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Reminder notifications. On the lock screen they can show only "Telos · 1 reminder". */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: Prefs,
) {
    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you set on tasks"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
    }

    fun canPost(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    suspend fun show(task: Task, reminderId: String, quiet: Boolean = false) {
        if (!canPost()) return
        createChannels()
        val settings = prefs.current()
        // A private task shows its cover title everywhere outside the app.
        val title = task.coverTitle?.takeIf { it.isNotBlank() } ?: task.title
        val text = Format.due(task.dueDate, task.dueMinute, LocalDate.now()) ?: "Reminder"
        val id = notificationId(task.id)

        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TASK, task.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String, label: String, code: Int) = NotificationCompat.Action(
            0, label,
            PendingIntent.getBroadcast(
                context, id * 8 + code,
                Intent(context, ReminderReceiver::class.java).setAction(action)
                    .putExtra(ReminderReceiver.EXTRA_REMINDER, reminderId).putExtra(ReminderReceiver.EXTRA_TASK, task.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Telos")
            .setContentText("1 reminder")
            .build()

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(quiet)
            .setSilent(quiet)
            .setVisibility(if (settings.hideOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .apply { if (settings.hideOnLockScreen) setPublicVersion(publicVersion) }
            .addAction(action(ReminderReceiver.ACTION_COMPLETE, "Complete", 1))
            .addAction(action(ReminderReceiver.ACTION_SNOOZE_15, "15 min", 2))
            .addAction(action(ReminderReceiver.ACTION_SNOOZE_60, "1 hour", 3))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun cancel(taskId: String) = NotificationManagerCompat.from(context).cancel(notificationId(taskId))

    companion object {
        const val CHANNEL = "reminders"
        fun notificationId(taskId: String) = taskId.hashCode() and 0x0FFFFFFF
    }
}
