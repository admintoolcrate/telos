package cc.toolcrate.telos.domain.views

import cc.toolcrate.telos.domain.model.Completion
import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.Label
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Section
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import java.time.LocalDate

/** A task with what a row needs to show, and its open subtasks when shown as a tree. */
data class TaskItem(
    val task: Task,
    val labels: List<Label> = emptyList(),
    val project: Project? = null,
    val section: Section? = null,
    val goal: Goal? = null,
    val children: List<TaskItem> = emptyList(),
    val depth: Int = 0,
    val hasReminder: Boolean = false,
    /** Open subtasks / all subtasks, for the "2/5" hint on collapsed or flat rows. */
    val subtasksOpen: Int = 0,
    val subtasksTotal: Int = 0,
) {
    val id: String get() = task.id

    /** The tree flattened for a list, skipping the children of collapsed tasks. */
    fun flatten(): List<TaskItem> =
        if (task.collapsed) listOf(this) else listOf(this) + children.flatMap { it.flatten() }
}

data class ProjectGroup(val section: Section?, val items: List<TaskItem>)

data class TodayView(val earlier: List<TaskItem>, val today: List<TaskItem>)

data class CompletedEntry(val completion: Completion, val task: Task?, val project: Project?)

/**
 * The in-memory index the screens work from. Built once per snapshot; "live" means not trashed,
 * not deleted, and not in an archived or trashed project.
 */
class Index(val snapshot: Snapshot) {
    val projects: Map<String, Project> = snapshot.projects.filter { it.deletedAt == null }.associateBy { it.id }
    val sections: Map<String, Section> = snapshot.sections.filter { it.deletedAt == null }.associateBy { it.id }
    val labels: Map<String, Label> = snapshot.labels.filter { it.deletedAt == null }.associateBy { it.id }
    val goals: Map<String, Goal> = snapshot.goals.filter { it.deletedAt == null }.associateBy { it.id }
    val labelsByTask: Map<String, List<Label>> = snapshot.taskLabels.filter { it.deletedAt == null }
        .groupBy({ it.taskId }, { labels[it.labelId] }).mapValues { (_, v) -> v.filterNotNull().sortedBy { it.orderKey } }
    private val reminderTasks: Set<String> = snapshot.reminders.filter { it.deletedAt == null }.map { it.taskId }.toSet()

    fun projectLive(id: String): Boolean = projects[id]?.let { it.archivedAt == null && it.trashedAt == null } ?: false

    val liveTasks: List<Task> = snapshot.tasks.filter { it.deletedAt == null && it.trashedAt == null && projectLive(it.projectId) }
    val openTasks: List<Task> = liveTasks.filter { it.completedAt == null }
    val tasksById: Map<String, Task> = snapshot.tasks.associateBy { it.id }
    private val openChildren: Map<String?, List<Task>> = openTasks.groupBy { it.parentId }
    private val liveChildren: Map<String?, List<Task>> = liveTasks.groupBy { it.parentId }

    fun item(t: Task, depth: Int = 0, withChildren: Boolean = false): TaskItem {
        val all = liveChildren[t.id].orEmpty()
        val kids = if (withChildren) openChildren[t.id].orEmpty().sortedBy { it.orderKey }.map { item(it, depth + 1, true) } else emptyList()
        return TaskItem(
            task = t, labels = labelsByTask[t.id].orEmpty(), project = projects[t.projectId],
            section = t.sectionId?.let(sections::get), goal = t.goalId?.let(goals::get), children = kids, depth = depth,
            hasReminder = t.id in reminderTasks, subtasksOpen = all.count { it.completedAt == null }, subtasksTotal = all.size,
        )
    }

    /** Roots of [tasks] (those whose parent isn't among them) as trees, in list order. */
    fun trees(tasks: List<Task>): List<TaskItem> {
        val ids = tasks.map { it.id }.toSet()
        return tasks.filter { it.parentId == null || it.parentId !in ids }.sortedBy { it.orderKey }.map { item(it, 0, true) }
    }

    fun inbox(): List<TaskItem> = trees(openTasks.filter { it.projectId == Project.INBOX_ID })

    fun today(today: LocalDate): TodayView {
        val day = today.toEpochDay()
        val due = openTasks.filter { it.dueDate != null && it.dueDate <= day }
        return TodayView(
            earlier = due.filter { it.dueDate!! < day }.sortedWith(dayOrder).map { item(it) },
            today = due.filter { it.dueDate == day }.sortedWith(dayOrder).map { item(it) },
        )
    }

    /** Open tasks due on each day from [from] for [days] days (empty days included). */
    fun upcoming(from: LocalDate, days: Int): List<Pair<LocalDate, List<TaskItem>>> {
        val byDay = openTasks.filter { it.dueDate != null }.groupBy { it.dueDate!! }
        return (0 until days).map { i ->
            val date = from.plusDays(i.toLong())
            date to byDay[date.toEpochDay()].orEmpty().sortedWith(dayOrder).map { item(it) }
        }
    }

    fun project(projectId: String): List<ProjectGroup> {
        val tasks = openTasks.filter { it.projectId == projectId }
        val sectionList = snapshot.sections.filter { it.projectId == projectId && it.deletedAt == null && it.archivedAt == null }
            .sortedBy { it.orderKey }
        val sectionIds = sectionList.map { it.id }.toSet()
        // A task whose section was removed shows outside any section.
        val loose = tasks.filter { it.sectionId == null || it.sectionId !in sectionIds }
        return listOf(ProjectGroup(null, trees(loose))) + sectionList.map { s -> ProjectGroup(s, trees(tasks.filter { it.sectionId == s.id })) }
    }

    fun withLabel(labelId: String): List<TaskItem> =
        openTasks.filter { t -> labelsByTask[t.id].orEmpty().any { it.id == labelId } }.sortedWith(dueOrder).map { item(it) }

    /** Projects as a tree (parent before children) with their depth; Inbox excluded. */
    fun projectTree(includeArchived: Boolean = false): List<Pair<Project, Int>> {
        val visible = projects.values.filter { !it.isInbox && it.trashedAt == null && (includeArchived || it.archivedAt == null) }
        val ids = visible.map { it.id }.toSet()
        val byParent = visible.groupBy { it.parentId?.takeIf { p -> p in ids } }
        val out = mutableListOf<Pair<Project, Int>>()
        fun walk(parent: String?, depth: Int) {
            for (p in byParent[parent].orEmpty().sortedBy { it.orderKey }) {
                out += p to depth
                walk(p.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    /**
     * The goal a task serves: its own link, else its parent task's, else its project's or the
     * nearest linked ancestor project's.
     */
    fun goalOf(task: Task): String? {
        task.goalId?.let { return it }
        var parent = task.parentId?.let(tasksById::get)
        val seen = mutableSetOf(task.id)
        while (parent != null && seen.add(parent.id)) {
            parent.goalId?.let { return it }
            parent = parent.parentId?.let(tasksById::get)
        }
        var project = projects[task.projectId]
        val seenP = mutableSetOf<String>()
        while (project != null && seenP.add(project.id)) {
            project.goalId?.let { return it }
            project = project.parentId?.let(projects::get)
        }
        return null
    }

    fun openCount(projectId: String): Int = openTasks.count { it.projectId == projectId }

    fun completed(): List<CompletedEntry> = snapshot.completions.filter { it.deletedAt == null }
        .sortedByDescending { it.completedAt }
        .map { c -> CompletedEntry(c, tasksById[c.taskId], tasksById[c.taskId]?.let { projects[it.projectId] }) }

    fun trashedTasks(): List<Task> = snapshot.tasks.filter { it.trashedAt != null && it.deletedAt == null }
        .let { trashed ->
            val ids = trashed.map { it.id }.toSet()
            trashed.filter { it.parentId == null || it.parentId !in ids }
        }.sortedByDescending { it.trashedAt }

    fun trashedProjects(): List<Project> = projects.values.filter { it.trashedAt != null }.sortedByDescending { it.trashedAt }

    fun archivedProjects(): List<Project> = projects.values.filter { it.archivedAt != null && it.trashedAt == null }

    companion object {
        const val TRASH_DAYS = 30

        fun daysLeftInTrash(t: Task, nowMillis: Long): Long {
            val trashed = t.trashedAt ?: return TRASH_DAYS.toLong()
            return maxOf(0, TRASH_DAYS - (nowMillis - trashed) / 86_400_000L)
        }

        /** Within a day: timed tasks first by time, then by priority, then the user's order. */
        val dayOrder: Comparator<Task> = compareBy<Task>(
            { it.dueDate ?: Long.MAX_VALUE },
            { it.dueMinute ?: Int.MAX_VALUE },
            { it.priority },
            { it.orderKey },
        )

        /** Dated tasks first (soonest), then undated, each by priority then order. */
        val dueOrder: Comparator<Task> = dayOrder
    }
}
