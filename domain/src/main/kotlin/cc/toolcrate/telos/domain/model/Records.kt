package cc.toolcrate.telos.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Columns every syncable record carries, so two copies of the database can be merged
 * record by record: last writer wins on [updatedAt], ties broken by [deviceId].
 * [deletedAt] is a tombstone: the row's content is scrubbed but the row stays, so a merge
 * never resurrects something that was deleted elsewhere.
 */
interface Syncable {
    val id: String
    val createdAt: Long
    val updatedAt: Long
    val deviceId: String
    val deletedAt: Long?
}

enum class ViewMode { LIST, BOARD }

enum class GoalStatus { ACTIVE, PAUSED, ACHIEVED }

enum class ReminderKind {
    /** At a fixed wall-clock date and time. */
    AT,

    /** A number of minutes before the task's due time; follows the task when it moves. */
    BEFORE,
}

@Serializable
@Entity(tableName = "projects", indices = [Index("parentId"), Index("goalId")])
data class Project(
    @PrimaryKey override val id: String,
    val name: String,
    val color: Int? = null,
    val parentId: String? = null,
    val goalId: String? = null,
    val viewMode: ViewMode = ViewMode.LIST,
    val orderKey: String,
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    /** When set, shown instead of [name] in widgets, notifications and shortcuts. */
    val coverTitle: String? = null,
    val isInbox: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable {
    companion object {
        const val INBOX_ID = "inbox"
    }
}

@Serializable
@Entity(tableName = "sections", indices = [Index("projectId")])
data class Section(
    @PrimaryKey override val id: String,
    val projectId: String,
    val name: String,
    val orderKey: String,
    val archivedAt: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable

@Serializable
@Entity(
    tableName = "tasks",
    indices = [Index("projectId"), Index("parentId"), Index("sectionId"), Index("goalId"), Index("dueDate")],
)
data class Task(
    @PrimaryKey override val id: String,
    val title: String,
    val notes: String = "",
    val projectId: String = Project.INBOX_ID,
    val sectionId: String? = null,
    val parentId: String? = null,
    val goalId: String? = null,
    /** 1 is the highest; 4 means no priority. */
    val priority: Int = 4,
    /** Due date as epoch day. */
    val dueDate: Long? = null,
    /** Due time as minute of the day (0..1439); null for an all-day task. */
    val dueMinute: Int? = null,
    /** Null: floating wall-clock time (the default). Otherwise the zone the time is pinned to. */
    val dueZone: String? = null,
    /** Encoded [cc.toolcrate.telos.domain.recurrence.Recurrence]. */
    val recurrence: String? = null,
    val orderKey: String,
    val completedAt: Long? = null,
    val collapsed: Boolean = false,
    val coverTitle: String? = null,
    /** Pinned as its goal's next step. */
    val pinnedNext: Boolean = false,
    val trashedAt: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable

@Serializable
@Entity(tableName = "labels")
data class Label(
    @PrimaryKey override val id: String,
    val name: String,
    val color: Int? = null,
    val orderKey: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable

/** A label on a task. A row of its own (with a tombstone) so removing a label merges cleanly. */
@Serializable
@Entity(tableName = "task_labels", indices = [Index("taskId"), Index("labelId")])
data class TaskLabel(
    @PrimaryKey override val id: String,
    val taskId: String,
    val labelId: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable

@Serializable
@Entity(tableName = "reminders", indices = [Index("taskId")])
data class Reminder(
    @PrimaryKey override val id: String,
    val taskId: String,
    val kind: ReminderKind,
    /** For [ReminderKind.AT]: epoch day and minute of the day, wall-clock. */
    val atDate: Long? = null,
    val atMinute: Int? = null,
    /** For [ReminderKind.BEFORE]. */
    val minutesBefore: Int? = null,
    /** Epoch millis; while in the future, the reminder fires then instead. */
    val snoozedUntil: Long? = null,
    /** Epoch millis of the last moment this reminder fired for, so it never fires twice. */
    val lastFiredFor: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable

/** One completion, kept as history. Recurring tasks log one per occurrence. */
@Serializable
@Entity(tableName = "completions", indices = [Index("taskId"), Index("completedAt")])
data class Completion(
    @PrimaryKey override val id: String,
    val taskId: String,
    /** The due date (epoch day) of the occurrence completed, if it had one. */
    val occurrenceDate: Long? = null,
    val completedAt: Long,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable

@Serializable
@Entity(tableName = "filters")
data class SavedFilter(
    @PrimaryKey override val id: String,
    val name: String,
    val query: String,
    val color: Int? = null,
    val orderKey: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey override val id: String,
    val title: String,
    /** The goal's telos: why it matters. */
    val why: String = "",
    val targetDate: Long? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val achievedAt: Long? = null,
    val coverTitle: String? = null,
    val orderKey: String,
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable

/** A note written during a quarterly review. */
@Serializable
@Entity(tableName = "review_entries")
data class ReviewEntry(
    @PrimaryKey override val id: String,
    /** For example "2026-Q4". */
    val quarter: String,
    val note: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deviceId: String,
    override val deletedAt: Long? = null,
) : Syncable
