package cc.toolcrate.telos.domain.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnvelopeTest {
    // Small memory cost keeps the suite fast; one test below uses the real settings.
    private fun fastKey(pass: String, salt: String = Envelope.newSalt()) = Envelope.deriveKey(pass.toCharArray(), KdfParams(m = 1024, t = 1, salt = salt))

    private val payload = """{"format":"telos","snapshot":{"tasks":[]}}""" + "x".repeat(5000)

    @Test
    fun `round trip with the passphrase`() {
        val file = Envelope.seal(payload, fastKey("correct horse"), 1)
        assertTrue(Envelope.looksLike(file))
        assertEquals(payload, Envelope.open(file, "correct horse".toCharArray()))
    }

    @Test
    fun `nothing readable leaves`() {
        val file = Envelope.seal("SECRET-TASK-TITLE", fastKey("pw"), 1)
        assertFalse("SECRET" in file)
        assertFalse(file.contains("pw\""))
    }

    @Test
    fun `wrong passphrase is refused`() {
        val file = Envelope.seal(payload, fastKey("right"), 1)
        assertThrows(WrongPassphrase::class.java) { Envelope.open(file, "wrong".toCharArray()) }
    }

    @Test
    fun `a tampered header or body is refused`() {
        val file = Envelope.seal(payload, fastKey("pw"), 1)
        val (header, body) = file.lines()
        val tamperedHeader = header.replace("\"createdAt\":1", "\"createdAt\":2") + "\n" + body
        assertThrows(WrongPassphrase::class.java) { Envelope.open(tamperedHeader, "pw".toCharArray()) }
        val flipped = body.toCharArray().also { it[10] = if (it[10] == 'A') 'B' else 'A' }.concatToString()
        assertThrows(WrongPassphrase::class.java) { Envelope.open(header + "\n" + flipped, "pw".toCharArray()) }
    }

    @Test
    fun `each backup uses a fresh nonce`() {
        val key = fastKey("pw")
        assertTrue(Envelope.seal(payload, key, 1) != Envelope.seal(payload, key, 1))
    }

    @Test
    fun `the same passphrase and salt give the same key, a new salt a new one`() {
        val salt = Envelope.newSalt()
        assertTrue(fastKey("pw", salt).bytes.contentEquals(fastKey("pw", salt).bytes))
        assertFalse(fastKey("pw").bytes.contentEquals(fastKey("pw").bytes))
    }

    @Test
    fun `real Argon2id settings use 64 MiB and 3 passes`() {
        val key = Envelope.deriveKey("a long passphrase".toCharArray())
        assertEquals(65_536, key.kdf.m)
        assertEquals(3, key.kdf.t)
        val file = Envelope.seal(payload, key, 1)
        assertEquals(payload, Envelope.open(file, "a long passphrase".toCharArray()))
    }
}
