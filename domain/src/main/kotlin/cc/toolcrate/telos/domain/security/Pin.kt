package cc.toolcrate.telos.domain.security

import cc.toolcrate.telos.domain.backup.Envelope
import cc.toolcrate.telos.domain.backup.KdfParams
import java.security.MessageDigest
import java.util.Base64

/** A stored PIN: only its Argon2id hash and the parameters, never the PIN. */
data class PinRecord(val hash: String, val kdf: KdfParams)

object Pin {
    /** Smaller than backup settings: unlocking should feel instant, and attempts are rate-limited. */
    fun params(salt: String = Envelope.newSalt()) = KdfParams(m = 19_456, t = 2, p = 1, salt = salt)

    fun valid(pin: String) = pin.length in 4..12 && pin.all { it.isDigit() }

    fun create(pin: String, params: KdfParams = params()): PinRecord {
        require(valid(pin)) { "A PIN is 4 to 12 digits." }
        return PinRecord(hash(pin, params), params)
    }

    fun verify(pin: String, record: PinRecord): Boolean =
        MessageDigest.isEqual(Base64.getDecoder().decode(hash(pin, record.kdf)), Base64.getDecoder().decode(record.hash))

    private fun hash(pin: String, params: KdfParams) = Base64.getEncoder().encodeToString(Envelope.deriveKey(pin.toCharArray(), params).bytes)
}

/**
 * How long to wait after [failures] wrong PINs: the first five are free, then 30 seconds,
 * doubling up to an hour. Calm, but enough to make guessing hopeless.
 */
fun pinBackoffMillis(failures: Int, free: Int = 5): Long {
    if (failures < free) return 0
    val steps = (failures - free).coerceAtMost(7)
    return minOf(30_000L shl steps, 60 * 60_000L)
}
