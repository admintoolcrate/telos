package cc.toolcrate.telos.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.DeviceIdentity
import cc.toolcrate.telos.data.FakeTime
import cc.toolcrate.telos.data.GoalRepository
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.inMemoryDb
import cc.toolcrate.telos.data.testRepository
import cc.toolcrate.telos.domain.model.ReminderKind
import cc.toolcrate.telos.domain.quickadd.TokenKind
import cc.toolcrate.telos.ui.quickadd.QuickAddViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class QuickAddSaveTest {
    private val time = FakeTime()
    private val db = inMemoryDb()
    private val repo = testRepository(db, time)
    private val goals = GoalRepository(db, DeviceIdentity(ApplicationProvider.getApplicationContext(), time))
    private val vm = QuickAddViewModel(repo, goals, Prefs(ApplicationProvider.getApplicationContext()), time)

    @Test
    fun `the brief's example creates the project and label it names`() = runTest {
        repo.ensureInbox()
        vm.start()
        vm.setText("Call dentist tomorrow 3pm #Health @phone p2 !30m")
        val task = vm.save()!!
        val snap = repo.snapshot.first()
        assertEquals("Call dentist", task.title)
        assertEquals(LocalDate.parse("2026-11-20").toEpochDay(), task.dueDate)
        assertEquals(15 * 60, task.dueMinute)
        assertEquals(2, task.priority)
        assertEquals("Health", snap.projects.first { it.id == task.projectId }.name)
        assertEquals(listOf("phone"), snap.labels.map { it.name })
        assertEquals(ReminderKind.BEFORE, snap.reminders.single().kind)
        assertEquals(30, snap.reminders.single().minutesBefore)
        // The sheet is cleared for the next task.
        assertEquals("", vm.state.value.text)
    }

    @Test
    fun `dismissing a chip and picking a date by hand`() = runTest {
        repo.ensureInbox()
        vm.start()
        vm.setText("Read the 1st chapter")
        val span = vm.preview(vm.state.value).parsed.spans.single()
        vm.dismiss(span.kind, span.text)
        assertNull(vm.preview(vm.state.value).date)
        vm.pickDate(LocalDate.parse("2026-12-24"))
        val task = vm.save()!!
        assertEquals("Read the 1st chapter", task.title)
        assertEquals(LocalDate.parse("2026-12-24").toEpochDay(), task.dueDate)
        assertEquals(TokenKind.DATE, span.kind)
    }

    @Test
    fun `a recurring task keeps its rule and lands on the first occurrence`() = runTest {
        repo.ensureInbox()
        vm.start()
        vm.setText("Pay rent every month on the 1st")
        val task = vm.save()!!
        assertEquals(LocalDate.parse("2026-12-01").toEpochDay(), task.dueDate)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1;MODE=SCHEDULE;ANCHOR=2026-12-01", task.recurrence)
    }

    @Test
    fun `empty titles are not saved`() = runTest {
        vm.start()
        vm.setText("tomorrow 3pm")
        assertNull(vm.save())
    }
}
