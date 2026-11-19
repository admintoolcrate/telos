package cc.toolcrate.telos.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.DeviceIdentity
import cc.toolcrate.telos.data.FakeTime
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.inMemoryDb
import cc.toolcrate.telos.data.testRepository
import cc.toolcrate.telos.domain.io.Codec
import cc.toolcrate.telos.domain.io.TodoistCsv
import cc.toolcrate.telos.domain.model.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DataTransferTest {
    private fun setup(time: FakeTime = FakeTime()): Pair<DataTransfer, cc.toolcrate.telos.data.Repository> {
        val db = inMemoryDb()
        val repo = testRepository(db, time)
        return DataTransfer(db, repo, time, DeviceIdentity(ApplicationProvider.getApplicationContext(), time)) to repo
    }

    @Test
    fun `export into a fresh install, then merge both ways`() = runTest {
        val (a, repoA) = setup()
        repoA.ensureInbox()
        val t = repoA.addTask(TaskDraft("Write chapter", labels = listOf("deep")))
        repoA.trash(listOf(repoA.addTask(TaskDraft("Old")).id))
        val json = a.exportJson()

        val (b, repoB) = setup()
        val r = b.merge(Codec.decode(json)!!.snapshot)
        assertTrue(r.fromIncoming > 0)
        val snap = repoB.snapshot.first()
        assertEquals("Write chapter", snap.tasks.first { it.id == t.id }.title)
        assertEquals(listOf("deep"), snap.labels.map { it.name })
        assertNotNull(snap.tasks.first { it.title == "Old" }.trashedAt)

        // A newer edit on B survives merging A's older copy again.
        repoB.updateTask(repoB.task(t.id)!!.copy(title = "Write chapter two", updatedAt = 0))
        b.merge(Codec.decode(json)!!.snapshot)
        assertEquals("Write chapter two", repoB.task(t.id)!!.title)
        // And merging the same thing twice changes nothing.
        assertEquals(0, b.merge(Codec.decode(b.exportJson())!!.snapshot).fromIncoming)
    }

    @Test
    fun `committing a Todoist import builds the project, sections, subtasks and labels`() = runTest {
        val (transfer, repo) = setup()
        repo.ensureInbox()
        val csv = "TYPE,CONTENT,DESCRIPTION,PRIORITY,INDENT,DATE\nsection,Errands,,,,\ntask,Buy stamps @errands,,1,1,tomorrow\ntask,Check prices,,4,2,\n"
        val plan = TodoistCsv.read(csv, LocalDateTime.parse("2026-11-19T10:00"), "Life")
        val project = transfer.commit(plan, null, null)
        val snap = repo.snapshot.first()
        assertEquals("Life", project.name)
        assertEquals(listOf("Errands"), snap.sections.filter { it.projectId == project.id }.map { it.name })
        val stamps = snap.tasks.first { it.title == "Buy stamps" }
        assertEquals(1, stamps.priority)
        assertNotNull(stamps.dueDate)
        assertEquals(stamps.id, snap.tasks.first { it.title == "Check prices" }.parentId)
        assertEquals(listOf("errands"), snap.labels.map { it.name })
    }

    @Test
    fun `the network gate stays shut while Drive backup is off`() = runTest {
        val gate = NetworkGate(Prefs(ApplicationProvider.getApplicationContext()))
        val e = runCatching { gate.request("GET", "https://www.googleapis.com/drive/v3/files") }.exceptionOrNull()
        assertTrue(e is NetworkBlocked)
    }
}
