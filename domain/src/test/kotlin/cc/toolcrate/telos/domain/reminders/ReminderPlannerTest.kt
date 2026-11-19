package cc.toolcrate.telos.domain.reminders

import cc.toolcrate.telos.domain.model.Reminder
import cc.toolcrate.telos.domain.model.ReminderKind
import cc.toolcrate.telos.domain.model.Task
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderPlannerTest {
    private val ny = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun task(due: String?, minute: Int? = null, zone: String? = null, done: Boolean = false, id: String = "t") = Task(
        id = id, title = "x", dueDate = due?.let { LocalDate.parse(it).toEpochDay() }, dueMinute = minute, dueZone = zone,
        orderKey = "V", completedAt = if (done) 1 else null, createdAt = 0, updatedAt = 0, deviceId = "d",
    )

    private fun before(min: Int, id: String = "r", task: String = "t", fired: Long? = null, snooze: Long? = null) = Reminder(
        id = id, taskId = task, kind = ReminderKind.BEFORE, minutesBefore = min, lastFiredFor = fired, snoozedUntil = snooze,
        createdAt = 0, updatedAt = 0, deviceId = "d",
    )

    private fun at(date: String, minute: Int, id: String = "r") = Reminder(
        id = id, taskId = "t", kind = ReminderKind.AT, atDate = LocalDate.parse(date).toEpochDay(), atMinute = minute,
        createdAt = 0, updatedAt = 0, deviceId = "d",
    )

    private fun plan(tasks: List<Task>, rs: List<Reminder>, now: String, zone: ZoneId = ny) =
        ReminderPlanner.plan(tasks, rs, Instant.parse(now), zone)

    @Test
    fun `thirty minutes before a timed task`() {
        val p = plan(listOf(task("2026-11-20", 15 * 60)), listOf(before(30)), "2026-11-19T12:00:00Z")
        assertEquals(Instant.parse("2026-11-20T19:30:00Z"), p.single().fireAt) // 14:30 EST
    }

    @Test
    fun `before an all-day task counts back from 9am`() {
        val p = plan(listOf(task("2026-11-20")), listOf(before(0)), "2026-11-19T12:00:00Z")
        assertEquals(Instant.parse("2026-11-20T14:00:00Z"), p.single().fireAt)
    }

    @Test
    fun `reminder inside the spring-forward gap rings at the shifted time`() {
        // 02:30 doesn't exist on 2026-03-08 in New York; it becomes 03:30 EDT (07:30Z).
        val p = plan(listOf(task("2026-03-08", 2 * 60 + 30)), listOf(before(0)), "2026-03-07T12:00:00Z")
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), p.single().fireAt)
    }

    @Test
    fun `reminder in the fall-back overlap rings once, at the first half past one`() {
        val p = plan(listOf(task("2026-11-01", 90)), listOf(before(0)), "2026-10-31T12:00:00Z")
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), p.single().fireAt)
        // After it fires, the second 01:30 (an hour later) must not ring again.
        val fired = before(0, fired = Instant.parse("2026-11-01T05:30:00Z").toEpochMilli())
        assertTrue(plan(listOf(task("2026-11-01", 90)), listOf(fired), "2026-11-01T06:10:00Z").isEmpty())
    }

    @Test
    fun `a 30-minute-before reminder that crosses the DST change`() {
        // Due 03:15 on spring-forward day; 30 minutes earlier on the clock is 02:45, which doesn't
        // exist. The reminder is 30 real minutes before the due instant: 02:45 EST = 07:45Z.
        val p = plan(listOf(task("2026-03-08", 3 * 60 + 15)), listOf(before(30)), "2026-03-07T12:00:00Z")
        assertEquals(Instant.parse("2026-03-08T06:45:00Z"), p.single().fireAt)
    }

    @Test
    fun `floating times follow a time-zone change`() {
        val t = listOf(task("2026-11-20", 9 * 60))
        val inLondon = plan(t, listOf(before(0)), "2026-11-19T00:00:00Z", london).single().fireAt
        val inTokyo = plan(t, listOf(before(0)), "2026-11-19T00:00:00Z", tokyo).single().fireAt
        assertEquals(Instant.parse("2026-11-20T09:00:00Z"), inLondon)
        assertEquals(Instant.parse("2026-11-20T00:00:00Z"), inTokyo)
    }

    @Test
    fun `pinned times keep their instant across a zone change`() {
        val t = listOf(task("2026-11-20", 9 * 60, zone = "Europe/London"))
        assertEquals(
            plan(t, listOf(before(0)), "2026-11-19T00:00:00Z", london).single().fireAt,
            plan(t, listOf(before(0)), "2026-11-19T00:00:00Z", tokyo).single().fireAt,
        )
    }

    @Test
    fun `fired reminders don't ring again, but do after the task moves`() {
        val nominal = Instant.parse("2026-11-20T14:00:00Z").toEpochMilli()
        val fired = before(0, fired = nominal)
        assertTrue(plan(listOf(task("2026-11-20", 9 * 60)), listOf(fired), "2026-11-20T14:05:00Z").isEmpty())
        val moved = plan(listOf(task("2026-11-21", 9 * 60)), listOf(fired), "2026-11-20T14:05:00Z")
        assertEquals(Instant.parse("2026-11-21T14:00:00Z"), moved.single().fireAt)
    }

    @Test
    fun `missed while off is delivered now if recent and dropped if old`() {
        val t = listOf(task("2026-11-20", 9 * 60))
        val recent = plan(t, listOf(before(0)), "2026-11-20T20:00:00Z").single()
        assertTrue(recent.missed)
        assertEquals(Instant.parse("2026-11-20T20:00:00Z"), recent.fireAt)
        assertTrue(plan(t, listOf(before(0)), "2026-11-22T20:00:00Z").isEmpty())
    }

    @Test
    fun `snooze overrides the nominal time`() {
        val snooze = Instant.parse("2026-11-20T15:00:00Z").toEpochMilli()
        val nominal = Instant.parse("2026-11-20T14:00:00Z").toEpochMilli()
        val p = plan(listOf(task("2026-11-20", 9 * 60)), listOf(before(0, fired = nominal, snooze = snooze)), "2026-11-20T14:01:00Z")
        assertEquals(Instant.ofEpochMilli(snooze), p.single().fireAt)
    }

    @Test
    fun `absolute reminders, completed and undated tasks`() {
        assertEquals(
            Instant.parse("2026-11-25T13:00:00Z"),
            plan(listOf(task(null)), listOf(at("2026-11-25", 8 * 60)), "2026-11-19T00:00:00Z").single().fireAt,
        )
        assertTrue(plan(listOf(task(null)), listOf(before(10)), "2026-11-19T00:00:00Z").isEmpty())
        assertTrue(plan(listOf(task("2026-11-20", 600, done = true)), listOf(before(10)), "2026-11-19T00:00:00Z").isEmpty())
    }

    @Test
    fun `soonest first, capped`() {
        val tasks = (1..100).map { task("2026-12-01", it, id = "t$it") }
        val rs = (1..100).map { before(0, id = "r$it", task = "t$it") }
        val p = ReminderPlanner.plan(tasks, rs, Instant.parse("2026-11-19T00:00:00Z"), ny, limit = 64)
        assertEquals(64, p.size)
        assertEquals("r1", p.first().reminderId)
        assertTrue(p.zipWithNext().all { (a, b) -> a.fireAt <= b.fireAt })
    }

    @Test
    fun `leap day reminder`() {
        val p = plan(listOf(task("2028-02-29", 8 * 60)), listOf(before(60 * 24)), "2028-02-01T00:00:00Z")
        assertEquals(Instant.parse("2028-02-28T13:00:00Z"), p.single().fireAt)
    }
}
