package cc.toolcrate.telos.domain

import java.security.SecureRandom
import java.time.Clock

/**
 * UUIDv7 identifiers: time-ordered, generated on the device, never reused. Stable across
 * export, import and backup merges.
 */
object Ids {
    private val random = SecureRandom()

    fun newId(clock: Clock = Clock.systemUTC()): String {
        val ms = clock.millis()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        for (i in 0 until 6) bytes[i] = (ms ushr (8 * (5 - i))).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
