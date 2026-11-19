package cc.toolcrate.telos.domain.filter

import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.Label
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Section
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.model.TaskLabel
import cc.toolcrate.telos.domain.views.Index
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.ZoneOffset

class FilterQueryTest {
    private val today = LocalDate.parse("2026-11-19") // Thursday
    private fun day(n: Long) = today.plusDays(n).toEpochDay()

    private fun p(id: String, parent: String? = null, goal: String? = null) =
        Project(id, id.replaceFirstChar { it.uppercase() }, parentId = parent, goalId = goal, orderKey = id, isInbox = id == "inbox", createdAt = 0, updatedAt = 0, deviceId = "d")

    private fun t(id: String, due: Long? = null, minute: Int? = null, prio: Int = 4, project: String = "inbox", section: String? = null,
                  parent: String? = null, rule: String? = null, created: LocalDate = today, notes: String = "") =
        Task(id = id, title = id, notes = notes, projectId = project, sectionId = section, parentId = parent, priority = prio,
            dueDate = due, dueMinute = minute, recurrence = rule, orderKey = id,
            createdAt = created.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), updatedAt = 0, deviceId = "d")

    private val snapshot = Snapshot(
        projects = listOf(p("inbox"), p("work", goal = "g"), p("clients", parent = "work"), p("home")),
        sections = listOf(Section("s", "work", "Next up", "A", createdAt = 0, updatedAt = 0, deviceId = "d")),
        labels = listOf(
            Label("l1", "phone", orderKey = "A", createdAt = 0, updatedAt = 0, deviceId = "d"),
            Label("l2", "home-office", orderKey = "B", createdAt = 0, updatedAt = 0, deviceId = "d"),
        ),
        goals = listOf(Goal("g", "Ship the book", orderKey = "A", createdAt = 0, updatedAt = 0, deviceId = "d")),
        tasks = listOf(
            t("call-today-p1", due = day(0), minute = 600, prio = 1),
            t("email-today", due = day(0)),
            t("late", due = day(-2), prio = 2),
            t("soon", due = day(2), project = "work", section = "s"),
            t("later", due = day(10), project = "clients"),
            t("undated", project = "home", notes = "remember the receipt"),
            t("sub", parent = "undated", project = "home", created = today.minusDays(40)),
            t("repeat", due = day(1), rule = "FREQ=DAILY;ANCHOR=2026-11-19"),
        ),
        taskLabels = listOf(
            TaskLabel("a", "call-today-p1", "l1", 0, 0, "d"),
            TaskLabel("b", "late", "l1", 0, 0, "d"),
            TaskLabel("c", "soon", "l2", 0, 0, "d"),
        ),
    )
    private val ctx = EvalContext(Index(snapshot), today, ZoneOffset.UTC)

    private fun run(q: String) = FilterQuery.run(FilterQuery.parse(q, today).getOrThrow(), ctx).map { it.id }.toSet()

    @Test
    fun `the brief's examples`() {
        assertEquals(setOf("call-today-p1"), run("today & p1 & @phone"))
        assertEquals(setOf("late", "call-today-p1", "email-today", "repeat", "soon"), run("overdue | due before: +3d"))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = ';',
        value = [
            "today; call-today-p1,email-today",
            "tomorrow; repeat",
            "overdue; late",
            "no date; undated,sub",
            "no time; email-today,late,soon,later,repeat",
            "p2; late",
            "no priority; email-today,soon,later,undated,sub,repeat",
            "@phone; call-today-p1,late",
            "@home*; soon",
            "no labels; email-today,later,undated,sub,repeat",
            "#work; soon",
            "##work; soon,later",
            "/next up; soon",
            "^ship the book; soon,later",
            "recurring; repeat",
            "subtask; sub",
            "search: receipt; undated",
            "7 days; call-today-p1,email-today,soon,repeat",
            "next 2 days; call-today-p1,email-today,repeat",
            "due: tomorrow; repeat",
            "due after: +5d; later",
            "due before: today; late",
            "due: 2026-11-29; later",
            "created before: -30d; sub",
            "!no date & !today; late,soon,later,repeat",
            "(p1 | p2) & @phone; call-today-p1,late",
            "today | tomorrow & p1; call-today-p1,email-today",
            "all; call-today-p1,email-today,late,soon,later,undated,sub,repeat",
        ],
    )
    fun atoms(query: String, expected: String) {
        assertEquals(expected.split(',').toSet(), run(query), query)
    }

    @Test
    fun `natural dates in due clauses`() {
        // Next Friday from Thursday 19th is the 27th; "later" is due the 29th.
        assertEquals(setOf("later"), run("due after: next friday"))
        assertEquals(setOf("later"), run("due: nov 29"))
    }

    @Test
    fun `relative dates follow the day the filter runs`() {
        val q = FilterQuery.parse("due before: +3d", today).getOrThrow()
        val tomorrowCtx = EvalContext(Index(snapshot), today.plusDays(9), ZoneOffset.UTC)
        assertTrue("later" in FilterQuery.run(q, tomorrowCtx).map { it.id })
    }

    @Test
    fun `case and spacing don't matter`() {
        assertEquals(run("today & p1"), run("  TODAY&P1 "))
    }

    @ParameterizedTest(name = "{0} → {1} at {2}")
    @CsvSource(
        delimiter = ';',
        value = [
            "today &; Something is missing at the end; 7",
            "(today | p1; Missing ')'; 11",
            "today)|p1; Unmatched ')'; 5",
            "today & bananas; Not understood: “bananas”; 8",
            "due before: someday; Not a date: “someday”; 12",
            "@; Which label?; 0",
            "' '; Type a query, for example: today & p1; 0",
        ],
    )
    fun errors(query: String, message: String, position: Int) {
        val e = FilterQuery.parse(query.trim('\''), today).exceptionOrNull()!!.filterError()!!
        assertEquals(message, e.message)
        assertEquals(position, e.position)
    }
}
