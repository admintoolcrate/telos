package cc.toolcrate.telos.domain.ops

import cc.toolcrate.telos.domain.model.Completion
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import java.time.LocalDate
import java.time.ZoneId

/**
 * What completing a task changes. A recurring task stays the same row: its due date moves to the
 * next occurrence and a [Completion] is logged. A one-off task gets [Task.completedAt].
 * [before] is kept so the change can be undone exactly.
 */
data class CompleteResult(
    val before: Task,
    val after: Task,
    val completion: Completion,
    /** True when the task moved to its next occurrence instead of closing. */
    val recurred: Boolean,
)

object Completing {
    fun complete(task: Task, stamp: Stamp, zone: ZoneId): CompleteResult {
        val now = stamp.now()
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        val completion = Completion(
            id = stamp.newId(), taskId = task.id, occurrenceDate = task.dueDate, completedAt = now,
            createdAt = now, updatedAt = now, deviceId = stamp.deviceId,
        )
        val rule = task.recurrence?.let(RecurrenceCodec::decode)
        val advance = rule?.advance(task.dueDate?.let(LocalDate::ofEpochDay), today)
        val after = if (advance != null) {
            task.copy(
                dueDate = advance.due.toEpochDay(),
                recurrence = RecurrenceCodec.encode(advance.rule),
                updatedAt = now, deviceId = stamp.deviceId,
            )
        } else {
            task.copy(completedAt = now, updatedAt = now, deviceId = stamp.deviceId)
        }
        return CompleteResult(task, after, completion, recurred = advance != null)
    }

    /** Reopens a completed one-off task. */
    fun reopen(task: Task, stamp: Stamp): Task =
        task.copy(completedAt = null, updatedAt = stamp.now(), deviceId = stamp.deviceId)
}
