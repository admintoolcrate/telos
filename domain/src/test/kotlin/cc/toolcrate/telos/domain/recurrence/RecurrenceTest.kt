package cc.toolcrate.telos.domain.recurrence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

private fun d(s: String) = LocalDate.parse(s)
private fun Recurrence.take(n: Int, from: LocalDate = anchor) = occurrences(from).take(n).toList()
private fun dates(vararg s: String) = s.map(::d)

class RecurrenceTest {

    @Nested
    inner class Daily {
        @Test
        fun `every day`() {
            val r = Recurrence(Freq.DAILY, anchor = d("2026-12-30"))
            assertEquals(dates("2026-12-30", "2026-12-31", "2027-01-01"), r.take(3))
        }

        @Test
        fun `every 3 days keeps its rhythm from the anchor`() {
            val r = Recurrence(Freq.DAILY, interval = 3, anchor = d("2026-02-26"))
            assertEquals(dates("2026-02-26", "2026-03-01", "2026-03-04"), r.take(3))
            assertEquals(d("2026-03-04"), r.firstOnOrAfter(d("2026-03-02")))
        }

        @Test
        fun `never before the anchor`() {
            val r = Recurrence(Freq.DAILY, anchor = d("2026-05-10"))
            assertEquals(d("2026-05-10"), r.firstOnOrAfter(d("2026-01-01")))
        }
    }

    @Nested
    inner class Weekly {
        @Test
        fun `every weekday skips the weekend`() {
            val r = Recurrence(Freq.WEEKLY, byWeekdays = Recurrence.WEEKDAYS, anchor = d("2026-11-19")) // Thu
            assertEquals(dates("2026-11-19", "2026-11-20", "2026-11-23", "2026-11-24"), r.take(4))
        }

        @Test
        fun `every 2 weeks on the anchor's weekday`() {
            val r = Recurrence(Freq.WEEKLY, interval = 2, anchor = d("2026-11-16")) // Mon
            assertEquals(dates("2026-11-16", "2026-11-30", "2026-12-14", "2026-12-28", "2027-01-11"), r.take(5))
        }

        @Test
        fun `every 2 weeks across ISO week 53`() {
            // 2026 has 53 ISO weeks; the fortnightly rhythm must not slip at the year boundary.
            val r = Recurrence(Freq.WEEKLY, interval = 2, anchor = d("2026-12-21"))
            val got = r.take(4)
            assertEquals(dates("2026-12-21", "2027-01-04", "2027-01-18", "2027-02-01"), got)
            got.zipWithNext().forEach { (a, b) -> assertEquals(14, b.toEpochDay() - a.toEpochDay()) }
        }

        @Test
        fun `every 2 weeks on Mon and Wed, starting mid-week`() {
            val r = Recurrence(Freq.WEEKLY, interval = 2, byWeekdays = setOf(MONDAY, WEDNESDAY), anchor = d("2026-11-18")) // Wed
            // The anchor week's Monday is before the anchor, so it's skipped.
            assertEquals(dates("2026-11-18", "2026-11-30", "2026-12-02", "2026-12-14"), r.take(4))
        }
    }

    @Nested
    inner class Monthly {
        @Test
        fun `every month on the 1st`() {
            val r = Recurrence(Freq.MONTHLY, byMonthDays = listOf(1), anchor = d("2026-11-19"))
            assertEquals(dates("2026-12-01", "2027-01-01", "2027-02-01"), r.take(3))
        }

        @Test
        fun `the 31st clamps to short months and comes back`() {
            val r = Recurrence(Freq.MONTHLY, byMonthDays = listOf(31), anchor = d("2027-01-31"))
            assertEquals(
                dates("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31"),
                r.take(5),
            )
        }

        @Test
        fun `the 30th in February of a leap year`() {
            val r = Recurrence(Freq.MONTHLY, byMonthDays = listOf(30), anchor = d("2028-01-30"))
            assertEquals(dates("2028-01-30", "2028-02-29", "2028-03-30"), r.take(3))
        }

        @Test
        fun `anchored on the 31st without explicit day keeps the 31st`() {
            val r = Recurrence(Freq.MONTHLY, anchor = d("2026-01-31"))
            assertEquals(dates("2026-01-31", "2026-02-28", "2026-03-31"), r.take(3))
        }

        @Test
        fun `last day of the month`() {
            val r = Recurrence(Freq.MONTHLY, byMonthDays = listOf(-1), anchor = d("2027-12-15"))
            assertEquals(dates("2027-12-31", "2028-01-31", "2028-02-29", "2028-03-31"), r.take(4))
        }

        @Test
        fun `the 30th and 31st collapse to one date in February`() {
            val r = Recurrence(Freq.MONTHLY, byMonthDays = listOf(30, 31), anchor = d("2027-02-01"))
            assertEquals(dates("2027-02-28", "2027-03-30", "2027-03-31"), r.take(3))
        }

        @Test
        fun `every 3 months`() {
            val r = Recurrence(Freq.MONTHLY, interval = 3, byMonthDays = listOf(15), anchor = d("2026-11-15"))
            assertEquals(dates("2026-11-15", "2027-02-15", "2027-05-15"), r.take(3))
        }

        @Test
        fun `the 2nd Tuesday`() {
            val r = Recurrence(Freq.MONTHLY, ordinal = WeekdayOrdinal(2, TUESDAY), anchor = d("2026-11-01"))
            assertEquals(dates("2026-11-10", "2026-12-08", "2027-01-12"), r.take(3))
        }

        @Test
        fun `the last Friday`() {
            val r = Recurrence(Freq.MONTHLY, ordinal = WeekdayOrdinal(-1, FRIDAY), anchor = d("2026-11-01"))
            assertEquals(dates("2026-11-27", "2026-12-25", "2027-01-29"), r.take(3))
        }

        @Test
        fun `the 5th Monday only in months that have one`() {
            val r = Recurrence(Freq.MONTHLY, ordinal = WeekdayOrdinal(5, MONDAY), anchor = d("2026-01-01"))
            val got = r.take(4)
            assertEquals(dates("2026-03-30", "2026-06-29", "2026-08-31", "2026-11-30"), got)
            got.forEach { assertEquals(MONDAY, it.dayOfWeek) }
        }
    }

    @Nested
    inner class Yearly {
        @Test
        fun `Feb 29 falls on Feb 28 in common years`() {
            val r = Recurrence(Freq.YEARLY, anchor = d("2028-02-29"))
            assertEquals(dates("2028-02-29", "2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29"), r.take(5))
        }

        @Test
        fun `2100 is not a leap year`() {
            val r = Recurrence(Freq.YEARLY, anchor = d("2096-02-29"))
            assertEquals(dates("2096-02-29", "2097-02-28", "2098-02-28", "2099-02-28", "2100-02-28", "2104-02-29").take(5), r.take(5))
            assertEquals(d("2100-02-28"), r.firstOnOrAfter(d("2100-01-01")))
        }

        @Test
        fun `every 2 years`() {
            val r = Recurrence(Freq.YEARLY, interval = 2, anchor = d("2026-07-04"))
            assertEquals(dates("2026-07-04", "2028-07-04", "2030-07-04"), r.take(3))
        }

        @Test
        fun `last Monday of May`() {
            val r = Recurrence(Freq.YEARLY, byMonth = 5, ordinal = WeekdayOrdinal(-1, MONDAY), anchor = d("2026-01-01"))
            assertEquals(dates("2026-05-25", "2027-05-31", "2028-05-29"), r.take(3))
        }
    }

    @Nested
    inner class Limits {
        @Test
        fun `until stops the rule`() {
            val r = Recurrence(Freq.DAILY, anchor = d("2026-11-19"), until = d("2026-11-21"))
            assertEquals(dates("2026-11-19", "2026-11-20", "2026-11-21"), r.occurrences().toList())
            assertNull(r.advance(d("2026-11-21"), d("2026-11-21")))
        }

        @Test
        fun `count counts down to the last occurrence`() {
            var r: Recurrence = Recurrence(Freq.DAILY, anchor = d("2026-11-19"), remaining = 3)
            var due = d("2026-11-19")
            val seen = mutableListOf(due)
            while (true) {
                val next = r.advance(due, due) ?: break
                due = next.due
                r = next.rule
                seen += due
            }
            assertEquals(dates("2026-11-19", "2026-11-20", "2026-11-21"), seen)
        }
    }

    @Nested
    inner class Advance {
        private val daily = Recurrence(Freq.DAILY, anchor = d("2026-11-01"))

        @Test
        fun `on time moves to the next day`() {
            assertEquals(d("2026-11-20"), daily.advance(d("2026-11-19"), d("2026-11-19"))!!.due)
        }

        @Test
        fun `overdue catches up to today without a backlog`() {
            assertEquals(d("2026-11-19"), daily.advance(d("2026-11-14"), d("2026-11-19"))!!.due)
        }

        @Test
        fun `completed early moves past the current due date`() {
            assertEquals(d("2026-11-23"), daily.advance(d("2026-11-22"), d("2026-11-19"))!!.due)
        }

        @Test
        fun `weekly overdue lands on the next scheduled weekday on or after today`() {
            val r = Recurrence(Freq.WEEKLY, byWeekdays = setOf(MONDAY), anchor = d("2026-11-02"))
            // Due Mon 9th, done Thu 19th: next Monday is the 23rd.
            assertEquals(d("2026-11-23"), r.advance(d("2026-11-09"), d("2026-11-19"))!!.due)
        }

        @Test
        fun `monthly on the 31st keeps returning to the 31st after February`() {
            val r = Recurrence(Freq.MONTHLY, byMonthDays = listOf(31), anchor = d("2027-01-31"))
            val feb = r.advance(d("2027-01-31"), d("2027-01-31"))!!
            assertEquals(d("2027-02-28"), feb.due)
            assertEquals(d("2027-03-31"), feb.rule.advance(feb.due, feb.due)!!.due)
        }

        @ParameterizedTest(name = "{0} days after completion on {1} → {2}")
        @CsvSource(
            "3, 2026-11-19, 2026-11-22",
            "3, 2026-12-30, 2027-01-02",
            "1, 2028-02-28, 2028-02-29",
            "1, 2027-02-28, 2027-03-01",
        )
        fun `days after completion`(n: Int, done: String, expected: String) {
            val r = Recurrence(Freq.DAILY, interval = n, mode = RepeatMode.AFTER_COMPLETION, anchor = d("2026-01-01"))
            // The original due date doesn't matter in this mode.
            assertEquals(d(expected), r.advance(d("2020-01-01"), d(done))!!.due)
        }

        @Test
        fun `a month after completion clamps at the month end`() {
            val r = Recurrence(Freq.MONTHLY, mode = RepeatMode.AFTER_COMPLETION, anchor = d("2026-01-01"))
            assertEquals(d("2027-02-28"), r.advance(null, d("2027-01-31"))!!.due)
            assertEquals(d("2028-02-29"), r.advance(null, d("2028-01-31"))!!.due)
        }

        @Test
        fun `a year after completion on Feb 29`() {
            val r = Recurrence(Freq.YEARLY, mode = RepeatMode.AFTER_COMPLETION, anchor = d("2028-01-01"))
            assertEquals(d("2029-02-28"), r.advance(null, d("2028-02-29"))!!.due)
        }

        @Test
        fun `every week after completion, on a Monday`() {
            val r = Recurrence(Freq.WEEKLY, mode = RepeatMode.AFTER_COMPLETION, byWeekdays = setOf(MONDAY), anchor = d("2026-01-01"))
            // Done Thu 19th → a week later is Thu 26th → the next Monday is Nov 30th.
            assertEquals(d("2026-11-30"), r.advance(null, d("2026-11-19"))!!.due)
        }

        @Test
        fun `a task without a due date gets the first occurrence from today`() {
            val r = Recurrence(Freq.WEEKLY, byWeekdays = setOf(FRIDAY), anchor = d("2026-01-01"))
            assertEquals(d("2026-11-20"), r.advance(null, d("2026-11-19"))!!.due)
        }
    }

    /** Ten years of occurrences for a spread of rules: strictly increasing, every one matching. */
    @Nested
    inner class Properties {
        private val anchor = d("2026-01-31")
        private val rules = listOf(
            Recurrence(Freq.DAILY, anchor = anchor),
            Recurrence(Freq.DAILY, interval = 5, anchor = anchor),
            Recurrence(Freq.WEEKLY, byWeekdays = Recurrence.WEEKDAYS, anchor = anchor),
            Recurrence(Freq.WEEKLY, interval = 3, byWeekdays = setOf(TUESDAY, DayOfWeek.SATURDAY), anchor = anchor),
            Recurrence(Freq.MONTHLY, anchor = anchor),
            Recurrence(Freq.MONTHLY, byMonthDays = listOf(1, 15, -1), anchor = anchor),
            Recurrence(Freq.MONTHLY, interval = 2, ordinal = WeekdayOrdinal(-1, FRIDAY), anchor = anchor),
            Recurrence(Freq.YEARLY, anchor = d("2028-02-29")),
        )

        @Test
        fun `occurrences increase strictly and match the rule`() {
            val end = d("2046-01-01")
            for (r in rules) {
                val all = r.occurrences().takeWhile { it < end }.toList()
                assertTrue(all.size >= 10, "too few for $r")
                all.zipWithNext().forEach { (a, b) -> assertTrue(a < b, "$a !< $b in $r") }
                all.forEach { assertTrue(r.matches(it), "$it should match $r") }
                assertTrue(all.first() >= r.anchor)
            }
        }

        @Test
        fun `days between occurrences never match`() {
            for (r in rules) {
                val all = r.occurrences().takeWhile { it < d("2028-01-01") }.toSet()
                var day = r.anchor
                while (day < d("2028-01-01")) {
                    assertEquals(day in all, r.matches(day), "$day for $r")
                    day = day.plusDays(1)
                }
            }
        }

        @Test
        fun `advancing on time walks the occurrence list`() {
            for (r in rules) {
                val expected = r.occurrences().take(30).toList()
                var due = expected.first()
                var rule = r
                val walked = mutableListOf(due)
                repeat(29) {
                    val next = rule.advance(due, due)!!
                    due = next.due
                    rule = next.rule
                    walked += due
                }
                assertEquals(expected, walked, "for $r")
            }
        }
    }

    @Test
    fun `codec round trip`() {
        val rules = listOf(
            Recurrence(Freq.DAILY, anchor = d("2026-11-19")),
            Recurrence(Freq.WEEKLY, interval = 2, byWeekdays = setOf(MONDAY, WEDNESDAY), anchor = d("2026-11-19"), until = d("2027-06-01")),
            Recurrence(Freq.MONTHLY, byMonthDays = listOf(1, -1), anchor = d("2026-11-19"), remaining = 4),
            Recurrence(Freq.MONTHLY, ordinal = WeekdayOrdinal(-1, FRIDAY), anchor = d("2026-11-19")),
            Recurrence(Freq.YEARLY, byMonth = 2, byMonthDays = listOf(29), anchor = d("2028-02-29")),
            Recurrence(Freq.DAILY, interval = 3, mode = RepeatMode.AFTER_COMPLETION, anchor = d("2026-11-19")),
        )
        for (r in rules) assertEquals(r, RecurrenceCodec.decode(RecurrenceCodec.encode(r)))
        assertEquals(
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;MODE=SCHEDULE;ANCHOR=2026-11-19;UNTIL=2027-06-01",
            RecurrenceCodec.encode(rules[1]),
        )
    }

    @Test
    fun `codec rejects nonsense and ignores unknown keys`() {
        assertNull(RecurrenceCodec.decode("garbage"))
        assertNull(RecurrenceCodec.decode("FREQ=HOURLY;ANCHOR=2026-01-01"))
        assertEquals(
            Recurrence(Freq.DAILY, anchor = d("2026-01-01")),
            RecurrenceCodec.decode("FREQ=DAILY;ANCHOR=2026-01-01;FUTURE=1"),
        )
    }

    @Test
    fun descriptions() {
        val a = d("2026-11-16") // Monday
        assertEquals("Every day", Recurrence(Freq.DAILY, anchor = a).describe())
        assertEquals("Every 3 days", Recurrence(Freq.DAILY, interval = 3, anchor = a).describe())
        assertEquals("Every weekday", Recurrence(Freq.WEEKLY, byWeekdays = Recurrence.WEEKDAYS, anchor = a).describe())
        assertEquals("Every 2 weeks on Monday", Recurrence(Freq.WEEKLY, interval = 2, anchor = a).describe())
        assertEquals("Every week on Mon, Wed", Recurrence(Freq.WEEKLY, byWeekdays = setOf(WEDNESDAY, MONDAY), anchor = a).describe())
        assertEquals("Every month on the 1st", Recurrence(Freq.MONTHLY, byMonthDays = listOf(1), anchor = a).describe())
        assertEquals("Every month on the last day", Recurrence(Freq.MONTHLY, byMonthDays = listOf(-1), anchor = a).describe())
        assertEquals("Every month on the last Friday", Recurrence(Freq.MONTHLY, ordinal = WeekdayOrdinal(-1, FRIDAY), anchor = a).describe())
        assertEquals("Every month on the 22nd", Recurrence(Freq.MONTHLY, byMonthDays = listOf(22), anchor = a).describe())
        assertEquals("Every year on 29 February", Recurrence(Freq.YEARLY, anchor = d("2028-02-29")).describe())
        assertEquals("3 days after completion", Recurrence(Freq.DAILY, interval = 3, mode = RepeatMode.AFTER_COMPLETION, anchor = a).describe())
        assertEquals("Week after completion", Recurrence(Freq.WEEKLY, mode = RepeatMode.AFTER_COMPLETION, anchor = a).describe())
    }

    @Test
    fun `an impossible rule ends instead of looping`() {
        // February never has a 5th Monday... except in leap years starting on a Monday (2044).
        val r = Recurrence(Freq.YEARLY, byMonth = 2, ordinal = WeekdayOrdinal(5, MONDAY), anchor = d("2026-01-01"))
        assertEquals(d("2044-02-29"), r.firstOnOrAfter(d("2026-01-01")))
        val never = Recurrence(Freq.YEARLY, byMonth = 4, ordinal = WeekdayOrdinal(5, MONDAY), interval = 7, anchor = d("2026-01-01"), until = d("2030-01-01"))
        assertFalse(never.occurrences().any())
    }
}
