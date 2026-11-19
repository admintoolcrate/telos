package cc.toolcrate.telos.domain.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WallTimeTest {
    private val ny = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")
    private val lordHowe = ZoneId.of("Australia/Lord_Howe")
    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(date: String, hh: Int, mm: Int, zone: ZoneId) =
        WallTime.toInstant(LocalDate.parse(date), hh * 60 + mm, zone)

    @Test
    fun `spring-forward gap moves the time forward by the gap`() {
        // New York, 2026-03-08: 02:00 → 03:00. 02:30 doesn't exist and becomes 03:30 EDT.
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), at("2026-03-08", 2, 30, ny))
        // London, 2027-03-28: 01:00 → 02:00.
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), at("2027-03-28", 1, 30, london))
    }

    @Test
    fun `fall-back overlap uses the earlier instant`() {
        // New York, 2026-11-01: 01:30 happens twice; the first is EDT (UTC-4).
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), at("2026-11-01", 1, 30, ny))
        // London, 2026-10-25: 01:30 BST (UTC+1).
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), at("2026-10-25", 1, 30, london))
    }

    @Test
    fun `Lord Howe shifts by thirty minutes`() {
        // 2026-10-04: 02:00 → 02:30 (UTC+10:30 → +11). 02:15 is in the gap → 02:45 (+11).
        assertEquals(Instant.parse("2026-10-03T15:45:00Z"), at("2026-10-04", 2, 15, lordHowe))
        // 2027-04-04: 02:00 → 01:30; 01:45 happens twice, the first at +11.
        assertEquals(Instant.parse("2027-04-03T14:45:00Z"), at("2027-04-04", 1, 45, lordHowe))
    }

    @Test
    fun `a daily 9am stays 9am across a DST change`() {
        val before = at("2026-03-07", 9, 0, ny)
        val after = at("2026-03-08", 9, 0, ny)
        assertEquals(Duration.ofHours(23), Duration.between(before, after))
    }

    @Test
    fun `floating times follow the device zone, pinned times keep their instant`() {
        val day = LocalDate.parse("2026-11-19").toEpochDay()
        val floatingInLondon = WallTime.toInstant(day, 9 * 60, null, london)
        val floatingInKolkata = WallTime.toInstant(day, 9 * 60, null, kolkata)
        assertEquals(Instant.parse("2026-11-19T09:00:00Z"), floatingInLondon)
        assertEquals(Instant.parse("2026-11-19T03:30:00Z"), floatingInKolkata)
        val pinned = WallTime.toInstant(day, 9 * 60, "Europe/London", kolkata)
        assertEquals(floatingInLondon, pinned)
    }

    @Test
    fun `midnight and the last minute of the day`() {
        assertEquals(Instant.parse("2028-02-29T00:00:00Z"), at("2028-02-29", 0, 0, ZoneId.of("UTC")))
        assertEquals(Instant.parse("2028-02-29T23:59:00Z"), at("2028-02-29", 23, 59, ZoneId.of("UTC")))
    }
}
