package cc.toolcrate.telos.domain.goals

import cc.toolcrate.telos.domain.model.Completion
import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.GoalStatus
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GoalsTest {
    private val today = LocalDate.parse("2026-11-19")
    private val now = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    private val day = 86_400_000L

    private fun goal(id: String, status: GoalStatus = GoalStatus.ACTIVE, created: Long = now - 100 * day, achievedAt: Long? = null) =
        Goal(id, "Goal $id", why = "because", status = status, achievedAt = achievedAt, orderKey = id, createdAt = created, updatedAt = created, deviceId = "d")

    private fun task(id: String, goal: String? = null, project: String = "inbox", done: Boolean = false, due: Long? = null, prio: Int = 4,
                     parent: String? = null, pinned: Boolean = false, created: Long = now - 100 * day, rule: String? = null, key: String = id) =
        Task(id = id, title = id, goalId = goal, projectId = project, completedAt = if (done) created + day else null, dueDate = due,
            priority = prio, parentId = parent, pinnedNext = pinned, recurrence = rule, orderKey = key, createdAt = created, updatedAt = created, deviceId = "d")

    private val projects = listOf(
        Project("inbox", "Inbox", orderKey = "0", isInbox = true, createdAt = 0, updatedAt = 0, deviceId = "d"),
        Project("book", "Book", goalId = "g", orderKey = "1", createdAt = 0, updatedAt = 0, deviceId = "d"),
        Project("ch1", "Chapter 1", parentId = "book", orderKey = "2", createdAt = 0, updatedAt = 0, deviceId = "d"),
    )

    private fun goals(vararg tasks: Task, completions: List<Completion> = emptyList(), goalList: List<Goal> = listOf(goal("g"))) =
        Goals(Index(Snapshot(projects = projects, goals = goalList, tasks = tasks.toList(), completions = completions)), now, ZoneOffset.UTC)

    @Test
    fun `progress counts one-off tasks from the goal, its projects and subprojects`() {
        val g = goals(
            task("direct", goal = "g", done = true),
            task("in-project", project = "book"),
            task("in-subproject", project = "ch1", done = true),
            task("practice", goal = "g", rule = "FREQ=DAILY;ANCHOR=2026-01-01"),
            task("elsewhere"),
        )
        val s = g.summary(goal("g"))
        assertEquals(2, s.done)
        assertEquals(3, s.total)
        assertEquals(2f / 3, s.progress, 0.001f)
        assertEquals(listOf("Book"), s.projects.map { it.name })
    }

    @Test
    fun `next step prefers the pinned task, then dated, then priority`() {
        val base = arrayOf(
            task("undated-p1", goal = "g", prio = 1),
            task("later", goal = "g", due = today.plusDays(5).toEpochDay()),
            task("sooner", goal = "g", due = today.plusDays(1).toEpochDay(), prio = 3),
        )
        assertEquals("sooner", goals(*base).summary(goal("g")).nextStep?.id)
        assertEquals("later", goals(*base.map { if (it.id == "later") it.copy(pinnedNext = true) else it }.toTypedArray()).summary(goal("g")).nextStep?.id)
    }

    @Test
    fun `next step descends to the first open subtask`() {
        val g = goals(
            task("write chapter", goal = "g", due = today.toEpochDay()),
            task("outline", goal = "g", parent = "write chapter", key = "A", done = true),
            task("first draft", goal = "g", parent = "write chapter", key = "B"),
            task("redraft", goal = "g", parent = "write chapter", key = "C"),
        )
        assertEquals("first draft", g.summary(goal("g")).nextStep?.id)
    }

    @Test
    fun `no open tasks means no next step`() {
        assertNull(goals(task("done", goal = "g", done = true)).summary(goal("g")).nextStep)
    }

    @Test
    fun `stalled after 30 days without activity, and never while paused`() {
        val quiet = goals(task("old", goal = "g", created = now - 60 * day)).summary(goal("g", created = now - 60 * day))
        assertTrue(quiet.stalled)
        val recent = goals(
            task("old", goal = "g", created = now - 60 * day),
            completions = listOf(Completion("c", "old", completedAt = now - 3 * day, createdAt = 0, updatedAt = 0, deviceId = "d")),
        ).summary(goal("g", created = now - 60 * day))
        assertFalse(recent.stalled)
        assertFalse(goals().summary(goal("g", GoalStatus.PAUSED, created = now - 90 * day)).stalled)
        assertFalse(goals().summary(goal("g", created = now - 5 * day)).stalled)
    }

    @Test
    fun `quarterly review sorts goals calmly`() {
        val list = listOf(
            goal("won", GoalStatus.ACHIEVED, achievedAt = now - 10 * day),
            goal("won-last-quarter", GoalStatus.ACHIEVED, achievedAt = now - 80 * day),
            goal("quiet", created = now - 60 * day),
            goal("busy", created = now - 2 * day),
            goal("resting", GoalStatus.PAUSED),
        )
        val r = goals(goalList = list).review(today)
        assertEquals("2026-Q4", r.quarter.toString())
        assertEquals(listOf("won"), r.achieved.map { it.goal.id })
        assertEquals(listOf("quiet"), r.stalled.map { it.goal.id })
        assertEquals(listOf("busy"), r.moving.map { it.goal.id })
        assertEquals(listOf("resting"), r.paused.map { it.goal.id })
    }

    @Test
    fun quarters() {
        assertEquals(Quarter(2026, 1), Quarter.of(LocalDate.parse("2026-03-31")))
        assertEquals(Quarter(2026, 2), Quarter.of(LocalDate.parse("2026-04-01")))
        assertEquals(LocalDate.parse("2028-03-31"), Quarter(2028, 1).end)
        assertEquals(Quarter(2025, 4), Quarter(2026, 1).previous())
        assertEquals(Quarter(2026, 4), Quarter.parse("2026-Q4"))
        assertEquals("Q4 2026", Quarter(2026, 4).label)
    }
}
