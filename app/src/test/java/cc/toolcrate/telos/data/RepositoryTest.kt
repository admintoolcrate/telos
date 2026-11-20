package cc.toolcrate.telos.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.TaskDraft
import cc.toolcrate.telos.domain.recurrence.Freq
import cc.toolcrate.telos.domain.recurrence.Recurrence
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: TelosDatabase
    private lateinit var time: FakeTime
    private lateinit var repo: Repository

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        time = FakeTime()
        repo = testRepository(db, time)
        repo.ensureInbox()
    }

    @After
    fun tearDown() = db.close()

    private fun day(s: String) = LocalDate.parse(s).toEpochDay()

    @Test
    fun `inbox exists once`() = runTest {
        repo.ensureInbox()
        val projects = repo.snapshot.first().projects
        assertEquals(1, projects.count { it.isInbox })
        assertEquals(Project.INBOX_ID, projects.single().id)
    }

    @Test
    fun `add task with labels creates them once`() = runTest {
        val a = repo.addTask(TaskDraft("Call dentist", labels = listOf("phone", "Health")))
        repo.addTask(TaskDraft("Call mum", labels = listOf("Phone")))
        val snap = repo.snapshot.first()
        assertEquals(setOf("phone", "Health"), snap.labels.map { it.name }.toSet())
        assertEquals(3, snap.taskLabels.size)
        assertTrue(snap.tasks.first { it.id == a.id }.orderKey < snap.tasks.first { it.id != a.id }.orderKey)
    }

    @Test
    fun `complete and undo a one-off task with subtasks`() = runTest {
        val parent = repo.addTask(TaskDraft("Pack"))
        val child = repo.addTask(TaskDraft("Socks", parentId = parent.id))
        val undo = repo.complete(parent.id)!!
        assertNotNull(repo.task(parent.id)!!.completedAt)
        assertNotNull(repo.task(child.id)!!.completedAt)
        assertEquals(1, repo.snapshot.first().completions.size)

        time.advanceMinutes(1)
        repo.undo(undo)
        assertNull(repo.task(parent.id)!!.completedAt)
        assertNull(repo.task(child.id)!!.completedAt)
        assertEquals(0, repo.snapshot.first().completions.size)
        // The tombstoned completion is still there for merging.
        assertEquals(1, db.completions().everything().size)
    }

    @Test
    fun `completing a recurring task moves it and reopens its subtasks`() = runTest {
        val rule = RecurrenceCodec.encode(Recurrence(Freq.WEEKLY, anchor = LocalDate.parse("2026-11-19")))
        val task = repo.addTask(TaskDraft("Review week", dueDate = day("2026-11-19"), dueMinute = 17 * 60, recurrence = rule))
        val step = repo.addTask(TaskDraft("Inbox zero", parentId = task.id))
        repo.complete(step.id)
        repo.complete(task.id)
        val after = repo.task(task.id)!!
        assertNull(after.completedAt)
        assertEquals(day("2026-11-26"), after.dueDate)
        assertEquals(17 * 60, after.dueMinute)
        assertNull(repo.task(step.id)!!.completedAt)
    }

    @Test
    fun `trash, restore and purge`() = runTest {
        val parent = repo.addTask(TaskDraft("Old plan"))
        val child = repo.addTask(TaskDraft("Step", parentId = parent.id))
        repo.trash(listOf(parent.id))
        assertNotNull(repo.task(child.id)!!.trashedAt)
        repo.restore(listOf(parent.id))
        assertNull(repo.task(child.id)!!.trashedAt)

        repo.trash(listOf(parent.id))
        time.advanceDays(29)
        assertEquals(0, repo.purgeExpiredTrash())
        time.advanceDays(2)
        assertEquals(2, repo.purgeExpiredTrash())
        val tomb = repo.task(parent.id)!!
        assertNotNull(tomb.deletedAt)
        assertEquals("", tomb.title)
        assertTrue(repo.snapshot.first().tasks.isEmpty())
    }

    @Test
    fun `restoring a subtask alone lifts it to the top level`() = runTest {
        val parent = repo.addTask(TaskDraft("Parent"))
        val child = repo.addTask(TaskDraft("Child", parentId = parent.id))
        repo.trash(listOf(parent.id))
        repo.restore(listOf(child.id))
        assertNull(repo.task(child.id)!!.parentId)
        assertNull(repo.task(child.id)!!.trashedAt)
    }

    @Test
    fun `every write bumps updatedAt`() = runTest {
        val t = repo.addTask(TaskDraft("Write"))
        time.advanceMinutes(5)
        repo.updateTask(t.copy(title = "Write more"))
        assertEquals(time.now(), repo.task(t.id)!!.updatedAt)
    }

    @Test
    fun `labels removed leave tombstones`() = runTest {
        val t = repo.addTask(TaskDraft("Tag me", labels = listOf("a", "b")))
        val a = repo.labelNamed("a")
        repo.setLabels(t.id, setOf(a.id))
        assertEquals(1, db.labels().linksFor(t.id).size)
        assertEquals(2, db.labels().everyLink().size)
    }

    @Test
    fun `moving a task carries its subtasks and refuses cycles`() = runTest {
        val p = repo.addProject("Health")
        val parent = repo.addTask(TaskDraft("Run"))
        val child = repo.addTask(TaskDraft("Shoes", parentId = parent.id))
        repo.move(parent.id, p.id, null, null, null, null)
        assertEquals(p.id, repo.task(child.id)!!.projectId)
        val failed = runCatching { repo.move(parent.id, p.id, null, child.id, null, null) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `full-text search finds titles and notes`() = runTest {
        repo.addTask(TaskDraft("Renew passport", notes = "Photos at the chemist"))
        repo.addTask(TaskDraft("Buy milk"))
        assertEquals(listOf("Renew passport"), db.tasks().search("chem*").map { it.title })
        assertEquals(listOf("Renew passport"), db.tasks().search("passport").map { it.title })
    }
}
