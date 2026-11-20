package cc.toolcrate.telos.domain.capture

import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SharedTextTest {
    @Test
    fun `a bare link becomes a reading task, with the link in notes`() {
        val c = SharedText.capture("https://www.example.com/essays/on-leisure/?utm=x")
        assertEquals("Read example.com/essays/on-leisure", c.title)
        assertEquals("https://www.example.com/essays/on-leisure/?utm=x", c.notes)
    }

    @Test
    fun `a subject from the sharing app wins`() {
        val c = SharedText.capture("Look at this https://example.com/a", subject = "On Leisure — an essay")
        assertEquals("On Leisure — an essay", c.title)
        assertEquals("https://example.com/a\nLook at this", c.notes)
    }

    @Test
    fun `text with a link keeps the text as the title`() {
        val c = SharedText.capture("Book the table https://example.com/booking.")
        assertEquals("Book the table", c.title)
        assertEquals("https://example.com/booking", c.notes)
    }

    @Test
    fun `plain text`() {
        assertEquals(Captured("Buy stamps", ""), SharedText.capture("  Buy stamps "))
        assertEquals(Captured("", ""), SharedText.capture(null))
    }

    @Test
    fun `long text is shortened and kept in full in notes`() {
        val long = "word ".repeat(80).trim()
        val c = SharedText.capture(long)
        assertEquals(200, c.title.length)
        assertEquals(long, c.notes)
    }

    @Test
    fun `widget rows use cover titles and put earlier first`() {
        val today = LocalDate.parse("2026-11-19")
        fun t(id: String, due: LocalDate, cover: String? = null, minute: Int? = null) = Task(
            id = id, title = "Real $id", coverTitle = cover, dueDate = due.toEpochDay(), dueMinute = minute, orderKey = id,
            createdAt = 0, updatedAt = 0, deviceId = "d",
        )
        val idx = Index(Snapshot(
            projects = listOf(Project(Project.INBOX_ID, "Inbox", orderKey = "0", isInbox = true, createdAt = 0, updatedAt = 0, deviceId = "d")),
            tasks = listOf(t("a", today, minute = 9 * 60), t("b", today.minusDays(1), cover = "Errand")),
        ))
        val rows = WidgetRows.today(idx, today)
        assertEquals(listOf("Errand", "Real a"), rows.map { it.title })
        assertEquals(listOf(true, false), rows.map { it.earlier })
        assertEquals("09:00", rows[1].meta)
    }
}
