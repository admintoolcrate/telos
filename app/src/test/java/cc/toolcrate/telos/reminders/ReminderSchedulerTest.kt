package cc.toolcrate.telos.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.FakeTime
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.Repository
import cc.toolcrate.telos.data.TelosDatabase
import cc.toolcrate.telos.data.inMemoryDb
import cc.toolcrate.telos.data.testRepository
import cc.toolcrate.telos.domain.model.ReminderSpec
import cc.toolcrate.telos.domain.model.TaskDraft
import cc.toolcrate.telos.domain.recurrence.Freq
import cc.toolcrate.telos.domain.recurrence.Recurrence
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val time = FakeTime(Instant.parse("2026-11-19T10:00:00Z"), ZoneId.of("America/New_York"))
    private lateinit var db: TelosDatabase
    private lateinit var repo: Repository
    private lateinit var scheduler: ReminderScheduler
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = inMemoryDb()
        repo = testRepository(db, time)
        repo.ensureInbox()
        scheduler = ReminderScheduler(app, db, time, Notifier(app, Prefs(app)))
        // Only this test's alarms.
        alarms.scheduledAlarms.toList().forEach { alarms.scheduledAlarms.remove(it) }
    }

    private fun day(s: String) = LocalDate.parse(s).toEpochDay()

    @Test
    fun `reconcile registers an exact alarm at the planned instant`() = runTest {
        repo.addTask(TaskDraft("Dentist", dueDate = day("2026-11-20"), dueMinute = 15 * 60, reminders = listOf(ReminderSpec.Before(30))))
        scheduler.reconcile()
        val next = alarms.nextScheduledAlarm
        assertNotNull(next)
        assertEquals(Instant.parse("2026-11-20T19:30:00Z").toEpochMilli(), next!!.triggerAtTime)
        assertEquals(1, db.alarms().all().size)
    }

    @Test
    fun `moving the task moves the alarm, completing it cancels`() = runTest {
        val t = repo.addTask(TaskDraft("Call", dueDate = day("2026-11-20"), dueMinute = 9 * 60, reminders = listOf(ReminderSpec.Before(0))))
        scheduler.reconcile()
        repo.updateTask(repo.task(t.id)!!.copy(dueMinute = 11 * 60))
        scheduler.reconcile()
        assertEquals(Instant.parse("2026-11-20T16:00:00Z").toEpochMilli(), db.alarms().all().single().triggerAt)
        repo.complete(t.id)
        scheduler.reconcile()
        assertTrue(db.alarms().all().isEmpty())
    }

    @Test
    fun `firing posts a notification that hides its content on the lock screen`() = runTest {
        val t = repo.addTask(TaskDraft("Private thing", dueDate = day("2026-11-19"), dueMinute = 6 * 60, reminders = listOf(ReminderSpec.Before(0))))
        val r = db.reminders().all().single()
        time.instant = Instant.parse("2026-11-19T11:00:00Z") // 06:00 EST
        scheduler.fire(r.id)
        val n = notifications.allNotifications.single()
        assertEquals("Private thing", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertEquals("Telos", n.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(3, n.actions.size)
        // Marked as fired: it won't ring again for the same moment.
        assertNotNull(repo.reminder(r.id)!!.lastFiredFor)
        scheduler.reconcile()
        assertEquals(1, notifications.allNotifications.size)
        assertEquals(t.id, db.tasks().get(t.id)!!.id)
    }

    @Test
    fun `cover titles stand in for the real title`() = runTest {
        val t = repo.addTask(TaskDraft("See Dr. Hale about results", dueDate = day("2026-11-19"), dueMinute = 6 * 60, reminders = listOf(ReminderSpec.Before(0))))
        repo.updateTask(repo.task(t.id)!!.copy(coverTitle = "Errand"))
        time.instant = Instant.parse("2026-11-19T11:00:00Z")
        scheduler.fire(db.reminders().all().single().id)
        assertEquals("Errand", notifications.allNotifications.single().extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `snooze re-registers fifteen minutes later`() = runTest {
        repo.addTask(TaskDraft("Stretch", dueDate = day("2026-11-19"), dueMinute = 6 * 60, reminders = listOf(ReminderSpec.Before(0))))
        val r = db.reminders().all().single()
        time.instant = Instant.parse("2026-11-19T11:00:00Z")
        scheduler.fire(r.id)
        repo.snooze(r.id, time.now() + 15 * 60_000)
        scheduler.reconcile()
        assertEquals(Instant.parse("2026-11-19T11:15:00Z").toEpochMilli(), db.alarms().all().single().triggerAt)
    }

    @Test
    fun `a time-zone change re-plans floating reminders`() = runTest {
        repo.addTask(TaskDraft("Standup", dueDate = day("2026-11-20"), dueMinute = 9 * 60, reminders = listOf(ReminderSpec.Before(0))))
        scheduler.reconcile()
        assertEquals(Instant.parse("2026-11-20T14:00:00Z").toEpochMilli(), db.alarms().all().single().triggerAt)
        time.zoneId = ZoneId.of("Europe/Berlin")
        scheduler.reconcile()
        assertEquals(Instant.parse("2026-11-20T08:00:00Z").toEpochMilli(), db.alarms().all().single().triggerAt)
    }

    @Test
    fun `missed while off is delivered once on reconcile`() = runTest {
        repo.addTask(TaskDraft("Pills", dueDate = day("2026-11-19"), dueMinute = 4 * 60, reminders = listOf(ReminderSpec.Before(0))))
        scheduler.reconcile() // 05:00 EST now; 04:00 was an hour ago.
        assertEquals(1, notifications.allNotifications.size)
        scheduler.reconcile()
        assertEquals(1, notifications.allNotifications.size)
    }

    @Test
    fun `recurring task reminders ring again for the next occurrence`() = runTest {
        val rule = RecurrenceCodec.encode(Recurrence(Freq.DAILY, anchor = LocalDate.parse("2026-11-19")))
        val t = repo.addTask(TaskDraft("Walk", dueDate = day("2026-11-19"), dueMinute = 6 * 60, recurrence = rule,
            reminders = listOf(ReminderSpec.Before(0), ReminderSpec.At(day("2026-11-19"), 5 * 60))))
        time.instant = Instant.parse("2026-11-19T11:30:00Z")
        scheduler.reconcile()
        repo.complete(t.id)
        val at = db.reminders().all().first { it.atDate != null }
        assertEquals(day("2026-11-20"), at.atDate)
        assertNull(at.lastFiredFor)
        scheduler.reconcile()
        assertEquals(
            listOf(Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-20T11:00:00Z")).map { it.toEpochMilli() },
            db.alarms().all().map { it.triggerAt }.sorted(),
        )
    }

    @Test
    fun `boot, update, clock and zone changes reach the system receiver`() {
        val pm = shadowOf(app.packageManager)
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) {
            val receivers = app.packageManager.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0)
            assertTrue(action, receivers.any { it.activityInfo.name.endsWith("SystemEventsReceiver") })
        }
        assertNotNull(pm)
    }
}
