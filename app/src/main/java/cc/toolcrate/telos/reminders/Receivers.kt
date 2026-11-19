package cc.toolcrate.telos.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cc.toolcrate.telos.data.Repository
import cc.toolcrate.telos.data.TimeSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
internal fun BroadcastReceiver.work(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Alarm firing, and the Complete / Snooze buttons on a reminder notification. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var repo: Repository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var time: TimeSource

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER)
        val taskId = intent.getStringExtra(EXTRA_TASK)
        work {
            when (intent.action) {
                ACTION_FIRE -> reminderId?.let { scheduler.fire(it) }
                ACTION_COMPLETE -> taskId?.let {
                    repo.complete(it)
                    notifier.cancel(it)
                    scheduler.reconcile()
                }
                ACTION_SNOOZE_15, ACTION_SNOOZE_60 -> if (reminderId != null) {
                    val minutes = if (intent.action == ACTION_SNOOZE_15) 15 else 60
                    repo.snooze(reminderId, time.now() + minutes * 60_000L)
                    taskId?.let(notifier::cancel)
                    scheduler.reconcile()
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "cc.toolcrate.telos.REMINDER_FIRE"
        const val ACTION_COMPLETE = "cc.toolcrate.telos.REMINDER_COMPLETE"
        const val ACTION_SNOOZE_15 = "cc.toolcrate.telos.REMINDER_SNOOZE_15"
        const val ACTION_SNOOZE_60 = "cc.toolcrate.telos.REMINDER_SNOOZE_60"
        const val EXTRA_REMINDER = "reminder"
        const val EXTRA_TASK = "task"
    }
}

/**
 * Re-registers alarms when they may have been lost or shifted: reboot, app update, the clock or
 * time zone changing, or exact-alarm permission being granted.
 */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        work { scheduler.reconcile() }
    }

    companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
