package cc.toolcrate.telos.domain.views

import cc.toolcrate.telos.domain.model.Label
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Section
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.model.TaskLabel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ViewsTest {
    private val today = LocalDate.parse("2026-11-19")
    private fun day(offset: Long) = today.plusDays(offset).toEpochDay()

    private fun task(id: String, project: String = Project.INBOX_ID, due: Long? = null, minute: Int? = null, p: Int = 4,
                     parent: String? = null, section: String? = null, key: String = id, done: Boolean = false, trashed: Boolean = false) =
        Task(id = id, title = id, projectId = project, dueDate = due, dueMinute = minute, priority = p, parentId = parent,
            sectionId = section, orderKey = key, completedAt = if (done) 1 else null, trashedAt = if (trashed) 1 else null,
            createdAt = 0, updatedAt = 0, deviceId = "d")

    private fun project(id: String, archived: Boolean = false, parent: String? = null, inbox: Boolean = false) =
        Project(id = id, name = id, orderKey = id, archivedAt = if (archived) 1 else null, parentId = parent, isInbox = inbox,
            createdAt = 0, updatedAt = 0, deviceId = "d")

    private val projects = listOf(project(Project.INBOX_ID, inbox = true), project("work"), project("old", archived = true), project("sub", parent = "work"))

    @Test
    fun `today separates earlier from today and sorts by time then priority`() {
        val idx = Index(Snapshot(projects = projects, tasks = listOf(
            task("late", due = day(-3)),
            task("untimed-p1", due = day(0), p = 1),
            task("nine", due = day(0), minute = 540, p = 4),
            task("eight", due = day(0), minute = 480, p = 3),
            task("tomorrow", due = day(1)),
            task("done", due = day(0), done = true),
            task("archived", project = "old", due = day(0)),
            task("trashed", due = day(0), trashed = true),
        )))
        val v = idx.today(today)
        assertEquals(listOf("late"), v.earlier.map { it.id })
        assertEquals(listOf("eight", "nine", "untimed-p1"), v.today.map { it.id })
    }

    @Test
    fun `upcoming includes empty days`() {
        val idx = Index(Snapshot(projects = projects, tasks = listOf(task("a", due = day(2)), task("b", due = day(2), p = 1))))
        val up = idx.upcoming(today, 7)
        assertEquals(7, up.size)
        assertEquals(listOf("b", "a"), up[2].second.map { it.id })
        assertTrue(up[0].second.isEmpty())
    }

    @Test
    fun `inbox is a tree in user order, collapsed children hidden when flattened`() {
        val idx = Index(Snapshot(projects = projects, tasks = listOf(
            task("b", key = "B"), task("a", key = "A"),
            task("a2", parent = "a", key = "2"), task("a1", parent = "a", key = "1"),
            task("a1x", parent = "a1", key = "1"),
            task("a3", parent = "a", key = "3", done = true),
        )))
        val roots = idx.inbox()
        assertEquals(listOf("a", "b"), roots.map { it.id })
        assertEquals(listOf("a", "a1", "a1x", "a2", "b"), roots.flatMap { it.flatten() }.map { it.id })
        assertEquals(listOf(0, 1, 2, 1, 0), roots.flatMap { it.flatten() }.map { it.depth })
        assertEquals(2 to 3, roots[0].subtasksOpen to roots[0].subtasksTotal)
    }

    @Test
    fun `project groups by section, orphaned section tasks fall back`() {
        val sections = listOf(
            Section("s1", "work", "Doing", "B", createdAt = 0, updatedAt = 0, deviceId = "d"),
            Section("s0", "work", "Next", "A", createdAt = 0, updatedAt = 0, deviceId = "d"),
            Section("gone", "work", "", "C", createdAt = 0, updatedAt = 0, deviceId = "d", deletedAt = 5),
        )
        val idx = Index(Snapshot(projects = projects, sections = sections, tasks = listOf(
            task("x", project = "work", section = "s1"), task("y", project = "work"), task("z", project = "work", section = "gone"),
        )))
        val groups = idx.project("work")
        assertEquals(listOf(null, "Next", "Doing"), groups.map { it.section?.name })
        assertEquals(listOf("y", "z"), groups[0].items.map { it.id })
        assertEquals(listOf("x"), groups[2].items.map { it.id })
    }

    @Test
    fun `labels attach to items and label views filter`() {
        val label = Label("l", "phone", orderKey = "A", createdAt = 0, updatedAt = 0, deviceId = "d")
        val idx = Index(Snapshot(projects = projects, labels = listOf(label), tasks = listOf(task("a"), task("b")),
            taskLabels = listOf(
                TaskLabel("tl1", "a", "l", 0, 0, "d"),
                TaskLabel("tl2", "b", "l", 0, 0, "d", deletedAt = 1),
            )))
        assertEquals(listOf("a"), idx.withLabel("l").map { it.id })
        assertEquals(listOf("phone"), idx.inbox().first().labels.map { it.name })
    }

    @Test
    fun `project tree nests and hides archived`() {
        val idx = Index(Snapshot(projects = projects))
        assertEquals(listOf("work" to 0, "sub" to 1), idx.projectTree().map { it.first.id to it.second })
        assertEquals(3, idx.projectTree(includeArchived = true).size)
    }

    @Test
    fun `trash shows only the top of trashed trees`() {
        val idx = Index(Snapshot(projects = projects, tasks = listOf(
            task("p", trashed = true), task("c", parent = "p", trashed = true), task("other", trashed = true),
        )))
        assertEquals(setOf("p", "other"), idx.trashedTasks().map { it.id }.toSet())
    }
}
