package cc.toolcrate.telos.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Wall-clock times (a date and a minute of the day) and the one place they become instants.
 *
 * - A time inside a DST spring-forward gap (02:30 on the night clocks jump from 02:00 to 03:00)
 *   moves forward by the gap: 02:30 → 03:30.
 * - A time inside a fall-back overlap (01:30 happens twice) uses the earlier of the two.
 *
 * Floating times (the default) are converted with the device's current zone each time, so
 * 09:00 stays 09:00 after travelling; pinned times use their stored zone.
 */
object WallTime {
    const val MINUTES_PER_DAY = 24 * 60

    fun toInstant(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Instant {
        require(minuteOfDay in 0 until MINUTES_PER_DAY) { "Minute of day out of range: $minuteOfDay" }
        val local = LocalDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        // ZonedDateTime.of applies exactly the gap/overlap rules above.
        return ZonedDateTime.of(local, zone).withEarlierOffsetAtOverlap().toInstant()
    }

    fun toInstant(epochDay: Long, minuteOfDay: Int, pinnedZone: String?, deviceZone: ZoneId): Instant =
        toInstant(LocalDate.ofEpochDay(epochDay), minuteOfDay, pinnedZone?.let(ZoneId::of) ?: deviceZone)

    fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute

    fun timeOf(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
}
