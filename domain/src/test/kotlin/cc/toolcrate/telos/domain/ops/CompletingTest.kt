package cc.toolcrate.telos.domain.ops

import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.recurrence.Freq
import cc.toolcrate.telos.domain.recurrence.Recurrence
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import cc.toolcrate.telos.domain.recurrence.RepeatMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CompletingTest {
    private val zone = ZoneId.of("Pacific/Auckland")

    private fun stampAt(instant: String) = Stamp(Clock.fixed(Instant.parse(instant), zone), "dev-a")

    private fun task(due: String?, rule: Recurrence? = null) = Task(
        id = "t1", title = "Water plants", dueDate = due?.let { LocalDate.parse(it).toEpochDay() },
        dueMinute = 9 * 60, recurrence = rule?.let(RecurrenceCodec::encode), orderKey = "V",
        createdAt = 0, updatedAt = 0, deviceId = "dev-b",
    )

    @Test
    fun `one-off task closes`() {
        val r = Completing.complete(task("2026-11-19"), stampAt("2026-11-19T01:00:00Z"), zone)
        assertFalse(r.recurred)
        assertNotNull(r.after.completedAt)
        assertEquals("dev-a", r.after.deviceId)
        assertEquals(r.before, task("2026-11-19"))
    }

    @Test
    fun `today is taken in the device zone, not UTC`() {
        // 2026-11-19T12:00Z is already the 20th in Auckland (UTC+13).
        val rule = Recurrence(Freq.DAILY, anchor = LocalDate.parse("2026-11-01"))
        val r = Completing.complete(task("2026-11-15", rule), stampAt("2026-11-19T12:00:00Z"), zone)
        assertTrue(r.recurred)
        assertEquals(LocalDate.parse("2026-11-20").toEpochDay(), r.after.dueDate)
        assertEquals(9 * 60, r.after.dueMinute)
        assertNull(r.after.completedAt)
        assertEquals(LocalDate.parse("2026-11-15").toEpochDay(), r.completion.occurrenceDate)
    }

    @Test
    fun `after-completion rule counts from the completion day`() {
        val rule = Recurrence(Freq.DAILY, interval = 3, mode = RepeatMode.AFTER_COMPLETION, anchor = LocalDate.parse("2026-11-01"))
        val r = Completing.complete(task("2026-11-10", rule), stampAt("2026-11-18T22:00:00Z"), zone) // 19th local
        assertEquals(LocalDate.parse("2026-11-22").toEpochDay(), r.after.dueDate)
    }

    @Test
    fun `last occurrence closes the task`() {
        val rule = Recurrence(Freq.DAILY, anchor = LocalDate.parse("2026-11-01"), remaining = 1)
        val r = Completing.complete(task("2026-11-19", rule), stampAt("2026-11-19T01:00:00Z"), zone)
        assertFalse(r.recurred)
        assertNotNull(r.after.completedAt)
    }

    @Test
    fun `count is decremented in the stored rule`() {
        val rule = Recurrence(Freq.DAILY, anchor = LocalDate.parse("2026-11-01"), remaining = 3)
        val r = Completing.complete(task("2026-11-19", rule), stampAt("2026-11-19T01:00:00Z"), zone)
        assertEquals(2, RecurrenceCodec.decode(r.after.recurrence!!)!!.remaining)
    }
}
