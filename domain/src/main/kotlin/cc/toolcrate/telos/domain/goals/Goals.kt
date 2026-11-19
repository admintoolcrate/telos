package cc.toolcrate.telos.domain.goals

import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.GoalStatus
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.domain.views.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class GoalSummary(
    val goal: Goal,
    val projects: List<Project>,
    /** Open tasks serving the goal, as trees. */
    val open: List<TaskItem>,
    /** One-off tasks done / all one-off tasks. Recurring tasks are practices, not progress. */
    val done: Int,
    val total: Int,
    val nextStep: TaskItem?,
    /** Epoch millis of the latest sign of life: a completion, a task added, an edit. */
    val lastActivity: Long,
    val stalled: Boolean,
) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** A quarter of the year, e.g. 2026-Q4. */
data class Quarter(val year: Int, val q: Int) {
    val start: LocalDate get() = LocalDate.of(year, (q - 1) * 3 + 1, 1)
    val end: LocalDate get() = YearMonth.of(year, q * 3).atEndOfMonth()
    val label: String get() = "Q$q $year"
    override fun toString() = "$year-Q$q"

    fun contains(date: LocalDate) = date in start..end
    fun previous() = if (q == 1) Quarter(year - 1, 4) else Quarter(year, q - 1)

    companion object {
        fun of(date: LocalDate) = Quarter(date.year, (date.monthValue - 1) / 3 + 1)
        fun parse(s: String): Quarter? = Regex("""(\d{4})-Q([1-4])""").matchEntire(s)?.let { Quarter(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
    }
}

data class Review(
    val quarter: Quarter,
    val achieved: List<GoalSummary>,
    val stalled: List<GoalSummary>,
    val moving: List<GoalSummary>,
    val paused: List<GoalSummary>,
)

class Goals(private val index: Index, private val now: Long, private val zone: ZoneId) {
    private val served: Map<String, List<Task>> = index.liveTasks.groupBy { index.goalOf(it) ?: "" }

    fun summary(goal: Goal): GoalSummary {
        val tasks = served[goal.id].orEmpty()
        val oneOff = tasks.filter { it.recurrence == null }
        val taskIds = tasks.map { it.id }.toSet()
        val completions = index.snapshot.completions.filter { it.deletedAt == null && it.taskId in taskIds }
        val last = listOfNotNull(
            goal.createdAt, goal.updatedAt,
            completions.maxOfOrNull { it.completedAt },
            tasks.maxOfOrNull { it.createdAt },
        ).max()
        val open = tasks.filter { it.completedAt == null }
        return GoalSummary(
            goal = goal,
            projects = index.projects.values.filter { it.goalId == goal.id && it.trashedAt == null }.sortedBy { it.orderKey },
            open = index.trees(open),
            done = oneOff.count { it.completedAt != null },
            total = oneOff.size,
            nextStep = nextStep(open),
            lastActivity = last,
            stalled = goal.status == GoalStatus.ACTIVE && now - last > STALLED_AFTER_MS,
        )
    }

    /**
     * One concrete action: the task pinned as next step, else the soonest (dated first), then
     * highest priority, then first in order. A task with open subtasks hands over to its first one,
     * so the step is always something you can just do.
     */
    fun nextStep(open: List<Task>): TaskItem? {
        if (open.isEmpty()) return null
        val ids = open.map { it.id }.toSet()
        val pinned = open.firstOrNull { it.pinnedNext }
        val first = pinned ?: open.filter { it.parentId == null || it.parentId !in ids }.minWithOrNull(stepOrder) ?: return null
        var step = first
        val seen = mutableSetOf(step.id)
        while (true) {
            val child = open.filter { it.parentId == step.id }.minWithOrNull(stepOrder) ?: break
            if (!seen.add(child.id)) break
            step = child
        }
        return index.item(step)
    }

    fun active(): List<GoalSummary> = visible().filter { it.status == GoalStatus.ACTIVE }.map(::summary)

    fun visible(): List<Goal> = index.goals.values.filter { it.trashedAt == null && it.archivedAt == null }.sortedBy { it.orderKey }

    fun review(today: LocalDate): Review {
        val quarter = Quarter.of(today)
        val all = index.goals.values.filter { it.trashedAt == null }.map(::summary)
        fun achievedIn(g: Goal) = g.status == GoalStatus.ACHIEVED && g.achievedAt?.let { quarter.contains(LocalDate.ofInstant(Instant.ofEpochMilli(it), zone)) } == true
        return Review(
            quarter = quarter,
            achieved = all.filter { achievedIn(it.goal) },
            stalled = all.filter { it.stalled && it.goal.archivedAt == null },
            moving = all.filter { it.goal.status == GoalStatus.ACTIVE && !it.stalled && it.goal.archivedAt == null },
            paused = all.filter { it.goal.status == GoalStatus.PAUSED && it.goal.archivedAt == null },
        )
    }

    companion object {
        const val STALLED_DAYS = 30
        const val STALLED_AFTER_MS = STALLED_DAYS * 24L * 60 * 60 * 1000

        private val stepOrder = compareBy<Task>({ it.dueDate == null }, { it.dueDate }, { it.dueMinute ?: Int.MAX_VALUE }, { it.priority }, { it.orderKey })
    }
}
