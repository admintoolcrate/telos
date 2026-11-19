package cc.toolcrate.telos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A time source whose clock the test moves by hand. */
class FakeTime(var instant: Instant = Instant.parse("2026-11-19T10:00:00Z"), var zoneId: ZoneId = ZoneId.of("Europe/London")) : TimeSource {
    override fun zone(): ZoneId = zoneId
    override fun clock(): Clock = Clock.fixed(instant, zoneId)
    fun advanceMinutes(m: Long) { instant = instant.plusSeconds(m * 60) }
    fun advanceDays(d: Long) { instant = instant.plusSeconds(d * 86_400) }
}

fun inMemoryDb(): TelosDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(), TelosDatabase::class.java,
).allowMainThreadQueries().build()

fun testRepository(db: TelosDatabase = inMemoryDb(), time: FakeTime = FakeTime()): Repository =
    Repository(db, DeviceIdentity(ApplicationProvider.getApplicationContext(), time), time)

@Suppress("unused")
private val utc = ZoneOffset.UTC

fun testAppViewModel(time: FakeTime = FakeTime()): cc.toolcrate.telos.ui.AppViewModel {
    val db = inMemoryDb()
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    return cc.toolcrate.telos.ui.AppViewModel(
        testRepository(db, time), Prefs(ctx), GoalRepository(db, DeviceIdentity(ctx, time)), time,
    )
}
