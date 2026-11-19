package cc.toolcrate.telos.domain.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinTest {
    private val fast = Pin.params().copy(m = 1024, t = 1)

    @Test
    fun `right and wrong PINs`() {
        val r = Pin.create("2468", fast)
        assertTrue(Pin.verify("2468", r))
        assertFalse(Pin.verify("2469", r))
        assertFalse("2468" in r.hash)
    }

    @Test
    fun `same PIN, different salts, different hashes`() {
        assertTrue(Pin.create("2468", fast).hash != Pin.create("2468", fast.copy(salt = Pin.params().salt)).hash)
    }

    @Test
    fun `PIN rules`() {
        assertTrue(Pin.valid("0000"))
        assertFalse(Pin.valid("123"))
        assertFalse(Pin.valid("12a4"))
        assertFalse(Pin.valid("1234567890123"))
        assertThrows(IllegalArgumentException::class.java) { Pin.create("12", fast) }
    }

    @Test
    fun backoff() {
        assertEquals(listOf(0L, 0L, 30_000L, 60_000L, 120_000L), listOf(0, 4, 5, 6, 7).map { pinBackoffMillis(it) })
        assertEquals(60 * 60_000L, pinBackoffMillis(50))
    }
}
