package cc.toolcrate.telos.data

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** The one source of "now" and the device's zone in :app. Tests swap in a fixed one. */
interface TimeSource {
    fun zone(): ZoneId
    fun clock(): Clock = Clock.system(zone())
    fun now(): Long = clock().millis()
    fun today(): LocalDate = LocalDate.now(clock())
}

/** Reads the zone each time, so a time-zone change is picked up without a restart. */
object SystemTimeSource : TimeSource {
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
