package cc.toolcrate.telos.domain.io

import cc.toolcrate.telos.domain.model.Label
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.model.TaskLabel
import cc.toolcrate.telos.domain.recurrence.Freq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.random.Random

class IoTest {
    private fun task(id: String, title: String = id, updated: Long = 1, device: String = "a", deleted: Long? = null) =
        Task(id = id, title = title, orderKey = "V", createdAt = 0, updatedAt = updated, deviceId = device, deletedAt = deleted)

    private val inbox = Project(Project.INBOX_ID, "Inbox", orderKey = "0", isInbox = true, createdAt = 0, updatedAt = 0, deviceId = "a")

    @Test
    fun `json round trip keeps everything, tombstones included`() {
        val s = Snapshot(
            projects = listOf(inbox),
            tasks = listOf(task("t", "Café, \"quoted\"\nline"), task("gone", "", deleted = 5)),
            labels = listOf(Label("l", "phone", orderKey = "A", createdAt = 0, updatedAt = 0, deviceId = "a")),
            taskLabels = listOf(TaskLabel("tl", "t", "l", 0, 0, "a")),
        )
        val back = Codec.decode(Codec.encode(s, 42))!!
        assertEquals(s, back.snapshot)
        assertEquals(42, back.exportedAt)
        assertTrue(Codec.looksLikeExport(Codec.encode(s, 1)))
        assertNull(Codec.decode("""{"format":"other","version":1,"exportedAt":1,"snapshot":{}}"""))
    }

    @Test
    fun `json from a newer version with unknown fields still reads`() {
        val text = """{"format":"telos","version":2,"exportedAt":1,"future":true,"snapshot":{"tasks":[{"id":"t","title":"x","orderKey":"V","createdAt":0,"updatedAt":0,"deviceId":"a","mood":"good"}]}}"""
        assertEquals("x", Codec.decode(text)!!.snapshot.tasks.single().title)
    }

    @Test
    fun `csv escapes and parses back`() {
        val s = Snapshot(projects = listOf(inbox), tasks = listOf(task("t", "Buy milk, eggs and \"good\" bread\nsoon")))
        val csv = CsvExport.write(s, ZoneOffset.UTC)
        val rows = Csv.parse(csv)
        assertEquals(CsvExport.header, rows[0])
        assertEquals("Buy milk, eggs and \"good\" bread\nsoon", rows[1][1])
        assertEquals("Inbox", rows[1][3])
    }

    @Test
    fun `merge picks the newer record, ties by device id`() {
        val local = Snapshot(tasks = listOf(task("a", "local", updated = 5), task("b", "local", updated = 9), task("c", "local", updated = 3, device = "x")))
        val remote = Snapshot(tasks = listOf(task("a", "remote", updated = 7), task("b", "remote", updated = 2), task("c", "remote", updated = 3, device = "y"), task("d", "remote")))
        val m = Merge.merge(local, remote)
        assertEquals(mapOf("a" to "remote", "b" to "local", "c" to "remote", "d" to "remote"), m.snapshot.tasks.associate { it.id to it.title })
        assertEquals(3, m.fromIncoming)
    }

    @Test
    fun `a newer tombstone wins over an older edit, and an older one doesn't resurrect`() {
        val deleted = task("t", "", updated = 10, deleted = 10)
        assertNotNull(Merge.merge(Snapshot(tasks = listOf(task("t", "edit", updated = 8))), Snapshot(tasks = listOf(deleted))).snapshot.tasks.single().deletedAt)
        assertNotNull(Merge.merge(Snapshot(tasks = listOf(deleted)), Snapshot(tasks = listOf(task("t", "old", updated = 3)))).snapshot.tasks.single().deletedAt)
    }

    @Test
    fun `merge is commutative and idempotent`() {
        val rnd = Random(3)
        repeat(50) {
            fun side(dev: String) = Snapshot(tasks = (1..20).filter { rnd.nextBoolean() }.map { task("t$it", "$dev$it", updated = rnd.nextLong(1, 5), device = dev) })
            val a = side("a")
            val b = side("b")
            val ab = Merge.merge(a, b).snapshot.tasks.sortedBy { it.id }
            val ba = Merge.merge(b, a).snapshot.tasks.sortedBy { it.id }
            assertEquals(ab, ba)
            assertEquals(ab, Merge.merge(Snapshot(tasks = ab), Snapshot(tasks = ab)).snapshot.tasks.sortedBy { it.id })
        }
    }

    private val now = LocalDateTime.parse("2026-11-19T10:00")

    @Test
    fun `todoist export with sections, subtasks, notes, dates and labels`() {
        val csv = "﻿TYPE,CONTENT,DESCRIPTION,PRIORITY,INDENT,AUTHOR,RESPONSIBLE,DATE,DATE_LANG,TIMEZONE,DURATION,DURATION_UNIT\r\n" +
            "section,Errands,,,,,,,,,,\r\n" +
            "task,Buy stamps @errands,For the cards,1,1,Me,,tomorrow,en,Europe/London,,\r\n" +
            "task,Check prices,,4,2,Me,,,en,,,\r\n" +
            "note,\"Ask about \"\"bulk\"\" deals\",,,,Me,,,,,,\r\n" +
            "task,Water plants,,3,1,Me,,every 3 days,en,,,\r\n" +
            ",,,,,,,,,,,\r\n" +
            "section,Later,,,,,,,,,,\r\n" +
            "task,Plan trip,,2,1,Me,,someday soon,en,,,\r\n"
        assertTrue(TodoistCsv.looksLike(csv))
        val plan = TodoistCsv.read(csv, now, "Errands project")
        assertEquals(listOf("Errands", "Later"), plan.sections)
        assertEquals(4, plan.taskCount)
        assertFalse(plan.prioritiesReversed)
        val stamps = plan.tasks[0]
        assertEquals("Buy stamps", stamps.title)
        assertEquals(listOf("errands"), stamps.labels)
        assertEquals(1, stamps.priority)
        assertEquals(LocalDate.parse("2026-11-20"), stamps.dueDate)
        assertEquals("Errands", stamps.section)
        assertEquals("Check prices", stamps.children.single().title)
        assertEquals("Ask about \"bulk\" deals", stamps.children.single().notes)
        assertEquals(Freq.DAILY, plan.tasks[1].recurrence?.freq)
        assertEquals(3, plan.tasks[1].recurrence?.interval)
        assertEquals("Later", plan.tasks[2].section)
        assertEquals(1, plan.warnings.size)
        assertEquals(setOf("errands"), plan.labels)
    }

    @Test
    fun `old-style todoist priorities are detected and can be flipped back`() {
        val csv = "TYPE,CONTENT,PRIORITY,INDENT,DATE\ntask,A,1,1,\ntask,B,1,1,\ntask,C,4,1,\n"
        val plan = TodoistCsv.read(csv, now)
        assertTrue(plan.prioritiesReversed)
        assertEquals(listOf(4, 4, 1), plan.tasks.map { it.priority })
        assertEquals(listOf(1, 1, 4), plan.flipPriorities().tasks.map { it.priority })
    }

    @Test
    fun `not a todoist file`() {
        assertFalse(TodoistCsv.looksLike("name,email\nx,y"))
        assertTrue(TodoistCsv.read("a,b\n1,2", now).warnings.isNotEmpty())
    }

    @Test
    fun `plain text list with bullets, indentation, headings and checkboxes`() {
        val text = """
            Groceries:
            - Milk
            - [x] Eggs
            # Home
            * Fix the shelf tomorrow 6pm @diy p2
                - Buy screws
              - Find drill
            Call mum every sunday
        """.trimIndent()
        val plan = PlainTextList.read(text, now)
        assertEquals(listOf("Groceries", "Home"), plan.sections)
        assertEquals(listOf("Milk", "Eggs", "Fix the shelf", "Call mum"), plan.tasks.map { it.title })
        assertTrue(plan.tasks[1].done)
        val shelf = plan.tasks[2]
        assertEquals(LocalDate.parse("2026-11-20"), shelf.dueDate)
        assertEquals(LocalTime.of(18, 0), shelf.dueTime)
        assertEquals(listOf("diy"), shelf.labels)
        assertEquals(2, shelf.priority)
        assertEquals(listOf("Buy screws", "Find drill"), shelf.children.map { it.title })
        assertEquals(Freq.WEEKLY, plan.tasks[3].recurrence?.freq)
        assertEquals("Home", plan.tasks[3].section)
        assertEquals(6, plan.taskCount)
    }

    @Test
    fun `plain text without understanding keeps lines literal`() {
        val plan = PlainTextList.read("Read the 1st chapter tomorrow", now, understand = false)
        assertEquals("Read the 1st chapter tomorrow", plan.tasks.single().title)
        assertNull(plan.tasks.single().dueDate)
    }
}
