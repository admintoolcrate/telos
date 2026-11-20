package cc.toolcrate.telos.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cc.toolcrate.telos.data.ScheduledAlarm
import cc.toolcrate.telos.data.TelosDatabase
import cc.toolcrate.telos.data.TimeSource
import cc.toolcrate.telos.domain.reminders.PlannedAlarm
import cc.toolcrate.telos.domain.reminders.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps AlarmManager in step with the database. [reconcile] is safe to call any time: after
 * writes, at boot, on time or zone changes, and from a periodic worker as a safety net.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: TelosDatabase,
    private val time: TimeSource,
    private val notifier: Notifier,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    /** Whether exact alarms are allowed; without them reminders may arrive a few minutes late. */
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    suspend fun reconcile() = lock.withLock {
        val now = Instant.ofEpochMilli(time.now())
        val plan = ReminderPlanner.plan(db.tasks().all(), db.reminders().all(), now, time.zone())
        val (dueNow, later) = plan.partition { !it.fireAt.isAfter(now) }

        val previous = db.alarms().all().associateBy { it.reminderId }
        val wanted = later.associateBy { it.reminderId }
        // Cancel alarms no longer wanted or at a different time.
        val stale = previous.values.filter { old -> wanted[old.reminderId]?.fireAt?.toEpochMilli() != old.triggerAt }
        stale.forEach { alarms.cancel(pending(it.reminderId, it.requestCode, 0)) }
        db.alarms().delete(stale.map { it.reminderId })

        val added = later.filter { p -> previous[p.reminderId]?.triggerAt != p.fireAt.toEpochMilli() }
        added.forEach { p -> set(p) }
        db.alarms().upsertAll(added.map { ScheduledAlarm(it.reminderId, requestCode(it.reminderId), it.fireAt.toEpochMilli()) })

        dueNow.forEach { deliver(it) }
    }

    /** Called when an alarm rings. Re-checks the plan, so a moved or completed task never rings stale. */
    suspend fun fire(reminderId: String) {
        val now = Instant.ofEpochMilli(time.now())
        val plan = ReminderPlanner.plan(db.tasks().all(), db.reminders().all(), now.plusSeconds(60), time.zone())
        plan.firstOrNull { it.reminderId == reminderId && !it.fireAt.isAfter(now.plusSeconds(60)) }?.let { deliver(it) }
        db.alarms().delete(listOf(reminderId))
        reconcile()
    }

    private suspend fun deliver(p: PlannedAlarm) {
        val task = db.tasks().get(p.taskId) ?: return
        notifier.show(task, p.reminderId, quiet = p.missed)
        val r = db.reminders().get(p.reminderId) ?: return
        db.reminders().upsert(r.copy(lastFiredFor = p.nominal.toEpochMilli(), snoozedUntil = null))
    }

    private fun set(p: PlannedAlarm) {
        val pi = pending(p.reminderId, requestCode(p.reminderId), PendingIntent.FLAG_UPDATE_CURRENT)
        val at = p.fireAt.toEpochMilli()
        if (exactAllowed()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(reminderId: String, code: Int, flags: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_REMINDER, reminderId),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        fun requestCode(reminderId: String): Int = reminderId.hashCode()
    }
}
