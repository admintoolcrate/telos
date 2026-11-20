package cc.toolcrate.telos.domain.reminders

import cc.toolcrate.telos.domain.model.Reminder
import cc.toolcrate.telos.domain.model.ReminderKind
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.time.WallTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * One alarm to register. [nominal] is the moment the reminder is *for* (stored as
 * `lastFiredFor` once delivered, so it never fires twice); [fireAt] is when to ring, which
 * differs when snoozed or when catching up on a missed one.
 */
data class PlannedAlarm(
    val reminderId: String,
    val taskId: String,
    val nominal: Instant,
    val fireAt: Instant,
    /** Missed while the phone was off or the app was stopped; deliver quietly, now. */
    val missed: Boolean = false,
)

/**
 * Works out which reminders should ring and when, from the records alone. Pure, so the
 * hard cases (DST, time-zone changes, reboots, snoozes) are unit-tested.
 */
object ReminderPlanner {
    /** All-day tasks have no time; "30 minutes before" counts back from this. */
    const val ALL_DAY_MINUTE = 9 * 60

    /** Missed reminders older than this are dropped instead of delivered late. */
    val MISSED_WINDOW: Duration = Duration.ofHours(24)

    /** AlarmManager allows ~500 per app; keep well under, and reconcile often. */
    const val LIMIT = 64

    /** The moment [r] is for, in [zone] (the device's) unless the task is pinned to another zone. */
    fun nominal(r: Reminder, task: Task, zone: ZoneId): Instant? = when (r.kind) {
        ReminderKind.AT -> {
            val day = r.atDate ?: return null
            val minute = r.atMinute ?: return null
            WallTime.toInstant(day, minute, task.dueZone, zone)
        }
        ReminderKind.BEFORE -> {
            val day = task.dueDate ?: return null
            val before = r.minutesBefore ?: return null
            WallTime.toInstant(day, task.dueMinute ?: ALL_DAY_MINUTE, task.dueZone, zone).minus(Duration.ofMinutes(before.toLong()))
        }
    }

    fun plan(tasks: Collection<Task>, reminders: Collection<Reminder>, now: Instant, zone: ZoneId, limit: Int = LIMIT): List<PlannedAlarm> {
        val byId = tasks.associateBy { it.id }
        return reminders.asSequence()
            .filter { it.deletedAt == null }
            .mapNotNull { r ->
                val task = byId[r.taskId] ?: return@mapNotNull null
                if (task.deletedAt != null || task.trashedAt != null || task.completedAt != null) return@mapNotNull null
                val nominal = nominal(r, task, zone) ?: return@mapNotNull null
                val snooze = r.snoozedUntil?.let(Instant::ofEpochMilli)
                when {
                    // Snoozed: rings at the snooze time, even if that's slightly past.
                    snooze != null -> PlannedAlarm(r.id, task.id, nominal, if (snooze.isBefore(now)) now else snooze, missed = snooze.isBefore(now))
                    r.lastFiredFor == nominal.toEpochMilli() -> null
                    // Already fired for an earlier moment and the task hasn't moved: nothing to do.
                    !nominal.isAfter(now) && Duration.between(nominal, now) > MISSED_WINDOW -> null
                    !nominal.isAfter(now) -> PlannedAlarm(r.id, task.id, nominal, now, missed = true)
                    else -> PlannedAlarm(r.id, task.id, nominal, nominal)
                }
            }
            .sortedBy { it.fireAt }
            .take(limit)
            .toList()
    }
}
