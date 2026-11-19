package cc.toolcrate.telos.data

import androidx.room.withTransaction
import cc.toolcrate.telos.domain.model.Completion
import cc.toolcrate.telos.domain.model.Label
import cc.toolcrate.telos.domain.model.OrderKeys
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Reminder
import cc.toolcrate.telos.domain.model.ReminderKind
import cc.toolcrate.telos.domain.model.ReminderSpec
import cc.toolcrate.telos.domain.model.SavedFilter
import cc.toolcrate.telos.domain.model.Section
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.model.TaskDraft
import cc.toolcrate.telos.domain.model.TaskLabel
import cc.toolcrate.telos.domain.ops.Completing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** What a change replaced, so the snackbar's Undo can put it back. */
data class Undo(
    val tasks: List<Task> = emptyList(),
    val completionsToRemove: List<Completion> = emptyList(),
)

/**
 * All writes to tasks, projects, sections and labels. Every write stamps `updatedAt` and
 * `deviceId` (see CLAUDE.md); deletion goes through the trash; nothing is hard-deleted.
 */
@Singleton
class Repository @Inject constructor(
    val db: TelosDatabase,
    private val device: DeviceIdentity,
    private val time: TimeSource,
) {
    private val tasks get() = db.tasks()
    private val stamp get() = device.stamp()

    /** Every live record (no tombstones), recomputed when any table changes. */
    val snapshot: Flow<Snapshot> = combine(
        combine(db.projects().observeAll(), db.sections().observeAll(), tasks.observeAll(), db.labels().observeAll(), db.labels().observeLinks()) { p, s, t, l, tl ->
            Snapshot(projects = p, sections = s, tasks = t, labels = l, taskLabels = tl)
        },
        combine(db.reminders().observeAll(), db.completions().observeAll(), db.filters().observeAll(), db.goals().observeAll(), db.reviews().observeAll()) { r, c, f, g, rv ->
            Snapshot(reminders = r, completions = c, filters = f, goals = g, reviews = rv)
        },
    ) { a, b -> a.copy(reminders = b.reminders, completions = b.completions, filters = b.filters, goals = b.goals, reviews = b.reviews) }

    suspend fun ensureInbox() {
        if (db.projects().get(Project.INBOX_ID) != null) return
        val now = stamp.now()
        db.projects().upsert(
            Project(
                id = Project.INBOX_ID, name = "Inbox", orderKey = "0", isInbox = true,
                createdAt = now, updatedAt = now, deviceId = device.deviceId,
            )
        )
    }

    // --- Tasks ---------------------------------------------------------------------------

    suspend fun addTask(draft: TaskDraft): Task = db.withTransaction {
        val s = stamp
        val now = s.now()
        val siblings = tasks.all().filter {
            it.projectId == draft.projectId && it.parentId == draft.parentId && it.sectionId == draft.sectionId
        }
        val task = Task(
            id = s.newId(), title = draft.title.trim(), notes = draft.notes, projectId = draft.projectId,
            sectionId = draft.sectionId, parentId = draft.parentId, goalId = draft.goalId,
            priority = draft.priority, dueDate = draft.dueDate, dueMinute = draft.dueMinute,
            recurrence = draft.recurrence, orderKey = OrderKeys.between(siblings.maxOfOrNull { it.orderKey }, null),
            createdAt = now, updatedAt = now, deviceId = s.deviceId,
        )
        tasks.upsert(task)
        if (draft.labels.isNotEmpty()) setLabelNames(task.id, draft.labels)
        draft.reminders.forEach { spec ->
            db.reminders().upsert(
                when (spec) {
                    is ReminderSpec.Before -> Reminder(
                        id = s.newId(), taskId = task.id, kind = ReminderKind.BEFORE, minutesBefore = spec.minutes,
                        createdAt = now, updatedAt = now, deviceId = s.deviceId,
                    )
                    is ReminderSpec.At -> Reminder(
                        id = s.newId(), taskId = task.id, kind = ReminderKind.AT, atDate = spec.epochDay,
                        atMinute = spec.minuteOfDay, createdAt = now, updatedAt = now, deviceId = s.deviceId,
                    )
                }
            )
        }
        task
    }

    /** Saves an edited task. Returns the previous version for undo. */
    suspend fun updateTask(task: Task): Undo = db.withTransaction {
        val before = tasks.get(task.id)
        tasks.upsert(task.copy(updatedAt = stamp.now(), deviceId = device.deviceId))
        Undo(tasks = listOfNotNull(before))
    }

    suspend fun task(id: String): Task? = tasks.get(id)

    fun observeTask(id: String): Flow<Task?> = tasks.observe(id)

    /**
     * Completes a task. A recurring task moves to its next occurrence and its completed subtasks
     * reopen; a one-off task closes along with its open subtasks.
     */
    suspend fun complete(id: String): Undo? = db.withTransaction {
        val task = tasks.get(id) ?: return@withTransaction null
        if (task.completedAt != null) return@withTransaction null
        val s = stamp
        val result = Completing.complete(task, s, time.zone())
        val descendants = descendantsOf(id)
        val changed = descendants.mapNotNull { child ->
            when {
                result.recurred && child.completedAt != null -> Completing.reopen(child, s)
                !result.recurred && child.completedAt == null -> child.copy(completedAt = s.now(), updatedAt = s.now(), deviceId = s.deviceId)
                else -> null
            }
        }
        tasks.upsertAll(listOf(result.after) + changed)
        db.completions().upsert(result.completion)
        if (result.recurred) resetReminderFiring(id, shiftDays = (result.after.dueDate ?: 0) - (task.dueDate ?: result.after.dueDate ?: 0))
        Undo(
            tasks = listOf(task) + descendants.filter { d -> changed.any { it.id == d.id } },
            completionsToRemove = listOf(result.completion),
        )
    }

    suspend fun reopen(id: String) = db.withTransaction {
        val task = tasks.get(id) ?: return@withTransaction
        tasks.upsert(Completing.reopen(task, stamp))
    }

    /** Puts back what [undo] recorded, as a new write (so it merges like any other edit). */
    suspend fun undo(undo: Undo) = db.withTransaction {
        val now = stamp.now()
        tasks.upsertAll(undo.tasks.map { it.copy(updatedAt = now, deviceId = device.deviceId) })
        db.completions().upsertAll(
            undo.completionsToRemove.map { it.copy(deletedAt = now, updatedAt = now, deviceId = device.deviceId) }
        )
    }

    /** Moves tasks (and their subtasks) to the trash. Restorable for 30 days. */
    suspend fun trash(ids: Collection<String>): Undo = db.withTransaction {
        val now = stamp.now()
        val affected = ids.flatMap { id -> listOfNotNull(tasks.get(id)) + descendantsOf(id) }
            .filter { it.trashedAt == null }.distinctBy { it.id }
        tasks.upsertAll(affected.map { it.copy(trashedAt = now, updatedAt = now, deviceId = device.deviceId) })
        Undo(tasks = affected)
    }

    suspend fun restore(ids: Collection<String>) = db.withTransaction {
        val now = stamp.now()
        val affected = ids.flatMap { id -> listOfNotNull(tasks.get(id)) + descendantsOf(id) }
            .filter { it.trashedAt != null && it.deletedAt == null }.distinctBy { it.id }
        // A restored subtask whose parent is still in the trash comes back at the top level.
        val restoredIds = affected.map { it.id }.toSet()
        tasks.upsertAll(
            affected.map { t ->
                val parent = t.parentId
                val parentGone = parent != null && parent !in restoredIds && tasks.get(parent)?.trashedAt != null
                t.copy(trashedAt = null, parentId = if (parentGone) null else t.parentId, updatedAt = now, deviceId = device.deviceId)
            }
        )
    }

    /** Turns trashed tasks into tombstones: content scrubbed, row kept for merging. */
    suspend fun purge(ids: Collection<String>) = db.withTransaction {
        val now = stamp.now()
        val doomed = ids.mapNotNull { tasks.get(it) }.filter { it.trashedAt != null && it.deletedAt == null }
        tasks.upsertAll(doomed.map { it.scrubbed(now) })
        val doomedIds = doomed.map { it.id }.toSet()
        db.labels().upsertLinks(db.labels().everyLink().filter { it.taskId in doomedIds && it.deletedAt == null }.map {
            it.copy(deletedAt = now, updatedAt = now, deviceId = device.deviceId)
        })
        db.reminders().upsertAll(db.reminders().all().filter { it.taskId in doomedIds }.map {
            it.copy(deletedAt = now, updatedAt = now, deviceId = device.deviceId)
        })
    }

    /** Purges everything trashed more than 30 days ago. Run daily. */
    suspend fun purgeExpiredTrash(): Int {
        val cutoff = time.now() - TRASH_DAYS * 24L * 60 * 60 * 1000
        val expired = tasks.trashedBefore(cutoff).map { it.id }
        purge(expired)
        return expired.size
    }

    /** Moves a task to another project/section/parent, placing it after [afterKey] (null: first). */
    suspend fun move(id: String, projectId: String, sectionId: String?, parentId: String?, before: String?, after: String?) =
        db.withTransaction {
            val task = tasks.get(id) ?: return@withTransaction
            require(parentId == null || parentId !in descendantsOf(id).map { it.id } + id) { "A task can't be its own subtask" }
            val now = stamp.now()
            tasks.upsert(
                task.copy(
                    projectId = projectId, sectionId = sectionId, parentId = parentId,
                    orderKey = OrderKeys.between(before, after), updatedAt = now, deviceId = device.deviceId,
                )
            )
            // Subtasks follow their parent to the new project.
            if (projectId != task.projectId) {
                tasks.upsertAll(descendantsOf(id).map { it.copy(projectId = projectId, updatedAt = now, deviceId = device.deviceId) })
            }
        }

    suspend fun setLabels(taskId: String, labelIds: Set<String>) = db.withTransaction {
        val now = stamp.now()
        val current = db.labels().linksFor(taskId)
        val removed = current.filter { it.labelId !in labelIds }
            .map { it.copy(deletedAt = now, updatedAt = now, deviceId = device.deviceId) }
        val existing = current.map { it.labelId }.toSet()
        val added = (labelIds - existing).map {
            TaskLabel(id = stamp.newId(), taskId = taskId, labelId = it, createdAt = now, updatedAt = now, deviceId = device.deviceId)
        }
        db.labels().upsertLinks(removed + added)
    }

    /** Sets labels by name, creating any that don't exist yet (case-insensitive match). */
    suspend fun setLabelNames(taskId: String, names: List<String>) = db.withTransaction {
        val ids = names.map { labelNamed(it).id }.toSet()
        setLabels(taskId, ids + db.labels().linksFor(taskId).map { it.labelId })
    }

    suspend fun labelNamed(name: String): Label {
        val clean = name.trim().removePrefix("@")
        db.labels().all().firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val now = stamp.now()
        val label = Label(
            id = stamp.newId(), name = clean, orderKey = OrderKeys.between(db.labels().all().maxOfOrNull { it.orderKey }, null),
            createdAt = now, updatedAt = now, deviceId = device.deviceId,
        )
        db.labels().upsert(label)
        return label
    }

    suspend fun saveLabel(label: Label) = db.labels().upsert(label.copy(updatedAt = stamp.now(), deviceId = device.deviceId))

    suspend fun deleteLabel(id: String) = db.withTransaction {
        val now = stamp.now()
        val label = db.labels().all().firstOrNull { it.id == id } ?: return@withTransaction
        db.labels().upsert(label.copy(name = "", deletedAt = now, updatedAt = now, deviceId = device.deviceId))
        db.labels().upsertLinks(db.labels().everyLink().filter { it.labelId == id && it.deletedAt == null }.map {
            it.copy(deletedAt = now, updatedAt = now, deviceId = device.deviceId)
        })
    }

    /** After a recurring task moves on: reminders are ready to ring again, and fixed-time ones move with it. */
    private suspend fun resetReminderFiring(taskId: String, shiftDays: Long) {
        val now = stamp.now()
        db.reminders().upsertAll(db.reminders().forTask(taskId).map {
            it.copy(
                lastFiredFor = null, snoozedUntil = null,
                atDate = if (it.kind == ReminderKind.AT) it.atDate?.plus(shiftDays) else it.atDate,
                updatedAt = now, deviceId = device.deviceId,
            )
        })
    }

    // --- Reminders -----------------------------------------------------------------------

    suspend fun addReminder(taskId: String, spec: ReminderSpec): Reminder {
        val s = stamp
        val now = s.now()
        val r = when (spec) {
            is ReminderSpec.Before -> Reminder(id = s.newId(), taskId = taskId, kind = ReminderKind.BEFORE, minutesBefore = spec.minutes, createdAt = now, updatedAt = now, deviceId = s.deviceId)
            is ReminderSpec.At -> Reminder(id = s.newId(), taskId = taskId, kind = ReminderKind.AT, atDate = spec.epochDay, atMinute = spec.minuteOfDay, createdAt = now, updatedAt = now, deviceId = s.deviceId)
        }
        db.reminders().upsert(r)
        return r
    }

    suspend fun deleteReminder(id: String) {
        val r = db.reminders().get(id) ?: return
        val now = stamp.now()
        db.reminders().upsert(r.copy(deletedAt = now, updatedAt = now, deviceId = device.deviceId))
    }

    suspend fun reminder(id: String): Reminder? = db.reminders().get(id)

    /** Records that [id] rang for [nominal], so it never rings for that moment again. */
    suspend fun markFired(id: String, nominal: Long) {
        val r = db.reminders().get(id) ?: return
        db.reminders().upsert(r.copy(lastFiredFor = nominal, snoozedUntil = null, updatedAt = stamp.now(), deviceId = device.deviceId))
    }

    suspend fun snooze(id: String, until: Long) {
        val r = db.reminders().get(id) ?: return
        db.reminders().upsert(r.copy(snoozedUntil = until, updatedAt = stamp.now(), deviceId = device.deviceId))
    }

    private suspend fun descendantsOf(id: String): List<Task> {
        val out = mutableListOf<Task>()
        val queue = ArrayDeque(listOf(id))
        val seen = mutableSetOf(id)
        while (queue.isNotEmpty()) {
            for (child in tasks.children(queue.removeFirst())) {
                if (seen.add(child.id)) {
                    out += child
                    queue += child.id
                }
            }
        }
        return out
    }

    // --- Projects and sections ------------------------------------------------------------

    suspend fun addProject(name: String, parentId: String? = null, goalId: String? = null): Project {
        val now = stamp.now()
        val project = Project(
            id = stamp.newId(), name = name.trim(), parentId = parentId, goalId = goalId,
            orderKey = OrderKeys.between(db.projects().all().maxOfOrNull { it.orderKey }, null),
            createdAt = now, updatedAt = now, deviceId = device.deviceId,
        )
        db.projects().upsert(project)
        return project
    }

    suspend fun saveProject(project: Project) =
        db.projects().upsert(project.copy(updatedAt = stamp.now(), deviceId = device.deviceId))

    suspend fun project(id: String): Project? = db.projects().get(id)

    suspend fun archiveProject(id: String, archived: Boolean) {
        val p = db.projects().get(id) ?: return
        require(!p.isInbox) { "The Inbox can't be archived" }
        saveProject(p.copy(archivedAt = if (archived) stamp.now() else null))
    }

    /** Trashes a project with all of its tasks. */
    suspend fun trashProject(id: String) = db.withTransaction {
        val p = db.projects().get(id) ?: return@withTransaction
        require(!p.isInbox) { "The Inbox can't be deleted" }
        val now = stamp.now()
        saveProject(p.copy(trashedAt = now))
        trash(tasks.all().filter { it.projectId == id && it.trashedAt == null }.map { it.id })
    }

    suspend fun restoreProject(id: String) = db.withTransaction {
        val p = db.projects().get(id) ?: return@withTransaction
        val trashedAt = p.trashedAt ?: return@withTransaction
        saveProject(p.copy(trashedAt = null))
        restore(tasks.all().filter { it.projectId == id && (it.trashedAt ?: -1) >= trashedAt }.map { it.id })
    }

    suspend fun addSection(projectId: String, name: String): Section {
        val now = stamp.now()
        val section = Section(
            id = stamp.newId(), projectId = projectId, name = name.trim(),
            orderKey = OrderKeys.between(db.sections().forProject(projectId).maxOfOrNull { it.orderKey }, null),
            createdAt = now, updatedAt = now, deviceId = device.deviceId,
        )
        db.sections().upsert(section)
        return section
    }

    suspend fun saveSection(section: Section) =
        db.sections().upsert(section.copy(updatedAt = stamp.now(), deviceId = device.deviceId))

    /** Removes a section; its tasks stay in the project, outside any section. */
    suspend fun deleteSection(id: String) = db.withTransaction {
        val s = db.sections().get(id) ?: return@withTransaction
        val now = stamp.now()
        db.sections().upsert(s.copy(name = "", deletedAt = now, updatedAt = now, deviceId = device.deviceId))
        tasks.upsertAll(tasks.all().filter { it.sectionId == id }.map {
            it.copy(sectionId = null, updatedAt = now, deviceId = device.deviceId)
        })
    }

    // --- Saved filters ---------------------------------------------------------------------

    suspend fun saveFilter(id: String?, name: String, query: String): SavedFilter {
        val now = stamp.now()
        val existing = id?.let { db.filters().get(it) }
        val f = existing?.copy(name = name.trim(), query = query.trim(), updatedAt = now, deviceId = device.deviceId)
            ?: SavedFilter(
                id = stamp.newId(), name = name.trim(), query = query.trim(),
                orderKey = OrderKeys.between(db.filters().everything().filter { it.deletedAt == null }.maxOfOrNull { it.orderKey }, null),
                createdAt = now, updatedAt = now, deviceId = device.deviceId,
            )
        db.filters().upsert(f)
        return f
    }

    suspend fun deleteFilter(id: String) {
        val f = db.filters().get(id) ?: return
        val now = stamp.now()
        db.filters().upsert(f.copy(name = "", query = "", deletedAt = now, updatedAt = now, deviceId = device.deviceId))
    }

    /** Full-text search over titles and notes; every word must match, as a prefix. */
    suspend fun search(text: String): List<Task> {
        val words = text.split(Regex("\\s+")).map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return tasks.search(words.joinToString(" ") { "$it*" })
    }

    /** Puts a reopened or restored project back in the list. */
    suspend fun unarchiveProject(id: String) = archiveProject(id, false)

    companion object {
        const val TRASH_DAYS = 30
    }
}

internal fun Task.scrubbed(now: Long) = copy(
    title = "", notes = "", coverTitle = null, recurrence = null, deletedAt = now, updatedAt = now,
)
