package cc.toolcrate.telos.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.FakeTime
import cc.toolcrate.telos.data.Prefs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = Prefs(ctx)
    private val time = FakeTime()

    @Test
    fun `off means never locked`() = runTest {
        val lock = AppLock(ctx, prefs, time)
        lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `locks on start and after the timeout, not on a quick switch`() = runTest {
        prefs.update { copy(appLock = true, lockTimeoutSeconds = 60) }
        val lock = AppLock(ctx, prefs, time)
        lock.onForeground()
        assertTrue(lock.locked.value)
        lock.unlock()
        lock.onBackground(); time.advanceMinutes(0); lock.onForeground()
        assertFalse(lock.locked.value)
        lock.onBackground(); time.advanceMinutes(2); lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `PIN unlocks, wrong guesses slow down`() = runTest {
        val lock = AppLock(ctx, prefs, time)
        lock.setPin("2468")
        assertTrue(lock.hasPin())
        repeat(4) { assertEquals(PinResult.Wrong, lock.checkPin("0000")) }
        assertTrue(lock.checkPin("0000") is PinResult.Wait)
        // Even the right PIN waits during the pause.
        assertTrue(lock.checkPin("2468") is PinResult.Wait)
        time.advanceMinutes(1)
        assertEquals(PinResult.Ok, lock.checkPin("2468"))
        assertFalse(lock.locked.value)
    }
}
