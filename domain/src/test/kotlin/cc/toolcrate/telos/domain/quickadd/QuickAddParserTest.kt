package cc.toolcrate.telos.domain.quickadd

import cc.toolcrate.telos.domain.model.ReminderSpec
import cc.toolcrate.telos.domain.recurrence.Freq
import cc.toolcrate.telos.domain.recurrence.Recurrence
import cc.toolcrate.telos.domain.recurrence.RepeatMode
import cc.toolcrate.telos.domain.recurrence.WeekdayOrdinal
import cc.toolcrate.telos.domain.recurrence.describe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Thursday 19 November 2026, 10:00, unless a test says otherwise. */
private val NOW = LocalDateTime.parse("2026-11-19T10:00")

private val CTX = ParseContext(
    now = NOW,
    projects = listOf(Named("p-health", "Health"), Named("p-home", "Home Renovation"), Named("p-work", "Work")),
    sections = listOf(NamedSection("s-calls", "Calls", "p-health"), NamedSection("s-next", "Next up", "p-work")),
    labels = listOf("phone", "errands", "deep work"),
    goals = listOf(Named("g-fit", "Run a marathon")),
)

private fun parse(text: String, ctx: ParseContext = CTX) = QuickAddParser.parse(text, ctx)
private fun d(s: String) = LocalDate.parse(s)
private fun t(s: String) = LocalTime.parse(s)

class QuickAddParserTest {

    @Test
    fun `the example from the brief`() {
        val p = parse("Call dentist tomorrow 3pm #Health @phone p2")
        assertEquals("Call dentist", p.title)
        assertEquals(d("2026-11-20"), p.dueDate)
        assertEquals(t("15:00"), p.dueTime)
        assertEquals(Ref.Existing("p-health", "Health"), p.project)
        assertEquals(listOf(Ref.Existing("phone", "phone")), p.labels)
        assertEquals(2, p.priority)
        assertEquals(
            listOf(TokenKind.DATE, TokenKind.TIME, TokenKind.PROJECT, TokenKind.LABEL, TokenKind.PRIORITY),
            p.spans.map { it.kind },
        )
        assertEquals("tomorrow", p.spans.first().text)
    }

    @Test
    fun `plain text stays plain`() {
        val p = parse("Buy milk")
        assertEquals("Buy milk", p.title)
        assertNull(p.dueDate)
        assertNull(p.dueTime)
        assertTrue(p.spans.isEmpty())
    }

    @Nested
    inner class Names {
        @Test
        fun `project names with spaces match the longest existing name`() {
            val p = parse("Sand the floor #home renovation tomorrow")
            assertEquals(Ref.Existing("p-home", "Home Renovation"), p.project)
            assertEquals("Sand the floor", p.title)
        }

        @Test
        fun `unknown project becomes a new one, quoted for spaces`() {
            assertEquals(Ref.New("Garden"), parse("Plant bulbs #Garden").project)
            val q = parse("Plant bulbs #\"Back garden\" p3")
            assertEquals(Ref.New("Back garden"), q.project)
            assertEquals("Plant bulbs", q.title)
        }

        @Test
        fun `section within the chosen project`() {
            val p = parse("Ring GP #Health /calls")
            assertEquals(Ref.Existing("s-calls", "Calls"), p.section)
            assertEquals("Ring GP", p.title)
        }

        @Test
        fun `section needs a project`() {
            val p = parse("Ratio 1 /calls")
            assertNull(p.section)
            assertEquals("Ratio 1 /calls", p.title)
        }

        @Test
        fun `several labels, multi-word existing ones, and new ones`() {
            val p = parse("Write essay @deep work @focus @Phone")
            assertEquals(listOf("deep work", "focus", "phone"), p.labels.map { it.name })
            assertEquals(Ref.New("focus"), p.labels[1])
            assertEquals("Write essay", p.title)
        }

        @Test
        fun `email addresses and hashtags inside words are not tokens`() {
            val p = parse("Email ana@example.com about C#")
            assertTrue(p.labels.isEmpty())
            assertNull(p.project)
            assertEquals("Email ana@example.com about C#", p.title)
        }

        @Test
        fun `goal link`() {
            assertEquals(Ref.Existing("g-fit", "Run a marathon"), parse("Long run ^run a marathon sat").goal)
        }
    }

    @Nested
    inner class Priority {
        @Test
        fun `p1 to p4 as words only`() {
            assertEquals(1, parse("Taxes P1").priority)
            assertNull(parse("Update app1 config").priority)
            assertNull(parse("Read p5").priority)
            assertEquals("Update app1 config", parse("Update app1 config").title)
        }
    }

    @Nested
    inner class Dates {
        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource(
            "Pay rent today, 2026-11-19",
            "Pay rent tod, 2026-11-19",
            "Pay rent tomorrow, 2026-11-20",
            "Pay rent tmrw, 2026-11-20",
            "Pay rent day after tomorrow, 2026-11-21",
            "Pay rent friday, 2026-11-20",
            "Pay rent on friday, 2026-11-20",
            "Pay rent thursday, 2026-11-19",
            "Pay rent next friday, 2026-11-27",
            "Pay rent next monday, 2026-11-23",
            "Pay rent this tuesday, 2026-11-24",
            "Pay rent in 3 days, 2026-11-22",
            "Pay rent in two weeks, 2026-12-03",
            "Pay rent in a month, 2026-12-19",
            "Pay rent +5d, 2026-11-24",
            "Pay rent +2w, 2026-12-03",
            "Pay rent next week, 2026-11-23",
            "Pay rent next month, 2026-12-01",
            "Pay rent end of month, 2026-11-30",
            "Pay rent eom, 2026-11-30",
            "Pay rent this weekend, 2026-11-21",
            "Pay rent jan 5, 2027-01-05",
            "Pay rent Dec 1st, 2026-12-01",
            "Pay rent 5 jan, 2027-01-05",
            "Pay rent the 5th of january, 2027-01-05",
            "Pay rent nov 19, 2026-11-19",
            "Pay rent nov 18, 2027-11-18",
            "Pay rent 2027-03-01, 2027-03-01",
            "Pay rent 1/12, 2026-12-01",
            "Pay rent 25/12/27, 2027-12-25",
            "Pay rent on the 1st, 2026-12-01",
            "Pay rent the 19th, 2026-11-19",
            "Pay rent by fri, 2026-11-20",
        )
        fun dates(text: String, expected: String) {
            val p = parse(text)
            assertEquals(d(expected), p.dueDate, text)
            assertEquals("Pay rent", p.title, text)
        }

        @Test
        fun `US order for numeric dates`() {
            assertEquals(d("2027-01-12"), parse("Pay 1/12", CTX.copy(dayFirst = false)).dueDate)
        }

        @Test
        fun `Feb 29 without a year is the next leap day`() {
            assertEquals(d("2028-02-29"), parse("Leap party feb 29").dueDate)
        }

        @Test
        fun `invalid dates are left as text`() {
            val p = parse("Ratio 31/02/2027 check")
            assertNull(p.dueDate)
            val q = parse("Item 13/13")
            assertNull(q.dueDate)
        }

        @Test
        fun `the 31st skips months without one`() {
            val ctx = CTX.copy(now = LocalDateTime.parse("2027-02-10T09:00"))
            assertEquals(d("2027-03-31"), parse("Invoice the 31st", ctx).dueDate)
        }

        @Test
        fun `short weekday names only when they clearly mean a date`() {
            assertNull(parse("Buy sun cream").dueDate)
            assertEquals("Buy sun cream", parse("Buy sun cream").title)
            assertNull(parse("I sat down to plan").dueDate)
            assertEquals(d("2026-11-21"), parse("Car wash sat").dueDate)
            assertEquals(d("2026-11-22"), parse("Call mum sun 6pm").dueDate)
            assertEquals(d("2026-11-21"), parse("Car wash on sat").dueDate)
        }

        @Test
        fun `months alone are not dates`() {
            assertNull(parse("Plan may holiday").dueDate)
        }

        @Test
        fun `only the first date is taken`() {
            val p = parse("Move meeting from friday to monday")
            assertEquals(d("2026-11-20"), p.dueDate)
            assertEquals("Move meeting from to monday", p.title)
        }

        @Test
        fun `on a Sunday 'next mon' is the following day when weeks start on Monday`() {
            val sunday = CTX.copy(now = LocalDateTime.parse("2026-11-22T10:00"))
            assertEquals(d("2026-11-23"), parse("x next mon", sunday).dueDate)
            val sundayStart = sunday.copy(weekStart = DayOfWeek.SUNDAY)
            assertEquals(d("2026-11-30"), parse("x next mon", sundayStart).dueDate)
        }

        @Test
        fun `next week honours the week start`() {
            assertEquals(d("2026-11-22"), parse("x next week", CTX.copy(weekStart = DayOfWeek.SUNDAY)).dueDate)
        }

        @Test
        fun `end of year rolls over`() {
            val nye = CTX.copy(now = LocalDateTime.parse("2026-12-31T23:00"))
            assertEquals(d("2027-01-01"), parse("x tomorrow", nye).dueDate)
            assertEquals(d("2027-01-01"), parse("x jan 1", nye).dueDate)
            assertEquals(d("2027-01-04"), parse("x next week", nye).dueDate)
        }
    }

    @Nested
    inner class Times {
        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource(
            "Standup 9am, 09:00",
            "Standup 9 am, 09:00",
            "Standup at 9:30am, 09:30",
            "Standup 12pm, 12:00",
            "Standup 12am, 00:00",
            "Standup 15:00, 15:00",
            "Standup at 18.30, 18:30",
            "Standup at 3, 15:00",
            "Standup at 9, 09:00",
            "Standup at 20, 20:00",
            "Standup noon, 12:00",
            "Standup in the evening, 19:00",
        )
        fun times(text: String, expected: String) {
            val p = parse(text)
            assertEquals(t(expected), p.dueTime, text)
            assertEquals("Standup", p.title, text)
        }

        @Test
        fun `a time alone means today, or tomorrow if it has passed`() {
            assertEquals(d("2026-11-19"), parse("Call 4pm").dueDate)
            assertEquals(d("2026-11-20"), parse("Call 8am").dueDate)
        }

        @Test
        fun `a passed time with an explicit today stays today`() {
            assertEquals(d("2026-11-19"), parse("Call today 8am").dueDate)
        }

        @Test
        fun `time before date`() {
            val p = parse("Dinner 7pm friday")
            assertEquals(d("2026-11-20"), p.dueDate)
            assertEquals(t("19:00"), p.dueTime)
            assertEquals("Dinner", p.title)
        }

        @Test
        fun `in two hours crosses midnight`() {
            val late = CTX.copy(now = LocalDateTime.parse("2026-11-19T23:12"))
            val p = parse("Check oven in 2 hours", late)
            assertEquals(d("2026-11-20"), p.dueDate)
            assertEquals(t("01:15"), p.dueTime)
        }

        @Test
        fun `tonight is 8pm, or just today after 8pm`() {
            val p = parse("Read tonight")
            assertEquals(d("2026-11-19"), p.dueDate)
            assertEquals(t("20:00"), p.dueTime)
            val late = parse("Read tonight", CTX.copy(now = LocalDateTime.parse("2026-11-19T23:30")))
            assertEquals(d("2026-11-19"), late.dueDate)
            assertNull(late.dueTime)
        }

        @Test
        fun `tomorrow just after midnight is the next calendar day`() {
            val p = parse("Sleep in tomorrow", CTX.copy(now = LocalDateTime.parse("2026-11-20T00:30")))
            assertEquals(d("2026-11-21"), p.dueDate)
        }

        @Test
        fun `times on DST change days are kept as wall-clock times`() {
            // 02:30 on the US spring-forward day is stored as typed; WallTime moves it to 03:30
            // only when a reminder is scheduled.
            val ctx = CTX.copy(now = LocalDateTime.parse("2026-03-07T12:00"))
            val p = parse("Feed cat tomorrow 2:30am", ctx)
            assertEquals(d("2026-03-08"), p.dueDate)
            assertEquals(t("02:30"), p.dueTime)
        }

        @Test
        fun `numbers that aren't times`() {
            assertNull(parse("Buy 3 apples").dueTime)
            assertNull(parse("Version 2.10 notes").dueTime)
            assertEquals("Buy 3 apples", parse("Buy 3 apples").title)
        }
    }

    @Nested
    inner class Recurring {
        private fun rule(text: String) = parse(text).recurrence!!

        @Test
        fun `every weekday`() {
            val p = parse("Standup every weekday 10:30")
            assertEquals(Recurrence.WEEKDAYS, p.recurrence!!.byWeekdays)
            assertEquals(Freq.WEEKLY, p.recurrence!!.freq)
            assertEquals(d("2026-11-19"), p.dueDate)
            assertEquals(t("10:30"), p.dueTime)
            assertEquals("Standup", p.title)
        }

        @Test
        fun `every day at a passed time starts tomorrow`() {
            val p = parse("Stretch every day at 8am")
            assertEquals(d("2026-11-20"), p.dueDate)
            assertEquals(t("08:00"), p.dueTime)
        }

        @Test
        fun `every 2 weeks`() {
            val r = rule("Bins every 2 weeks")
            assertEquals(Freq.WEEKLY, r.freq)
            assertEquals(2, r.interval)
            assertEquals(d("2026-11-19"), r.anchor)
        }

        @Test
        fun `every other week on monday`() {
            val p = parse("1:1 every other week on monday")
            assertEquals(2, p.recurrence!!.interval)
            assertEquals(setOf(DayOfWeek.MONDAY), p.recurrence!!.byWeekdays)
            assertEquals(d("2026-11-23"), p.dueDate)
            assertEquals("1:1", p.title)
        }

        @Test
        fun `every month on the 1st`() {
            val p = parse("Pay rent every month on the 1st")
            assertEquals(listOf(1), p.recurrence!!.byMonthDays)
            assertEquals(d("2026-12-01"), p.dueDate)
            assertEquals("Pay rent", p.title)
        }

        @Test
        fun `every 1st and 15th`() {
            val p = parse("Payroll every 1st and 15th")
            assertEquals(listOf(1, 15), p.recurrence!!.byMonthDays)
            assertEquals(d("2026-12-01"), p.dueDate)
        }

        @Test
        fun `every last day and every last friday`() {
            assertEquals(listOf(-1), rule("Report every last day").byMonthDays)
            val p = parse("Drinks every last fri")
            assertEquals(WeekdayOrdinal(-1, DayOfWeek.FRIDAY), p.recurrence!!.ordinal)
            assertEquals(d("2026-11-27"), p.dueDate)
        }

        @Test
        fun `every 2nd tuesday`() {
            val p = parse("Book club every 2nd tuesday 7pm")
            assertEquals(WeekdayOrdinal(2, DayOfWeek.TUESDAY), p.recurrence!!.ordinal)
            assertEquals(d("2026-12-08"), p.dueDate)
            assertEquals(t("19:00"), p.dueTime)
        }

        @Test
        fun `every mon, wed and fri`() {
            val p = parse("Gym every mon, wed and fri 7am")
            assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), p.recurrence!!.byWeekdays)
            assertEquals(d("2026-11-20"), p.dueDate)
            assertEquals("Gym", p.title)
        }

        @Test
        fun `every year on a date, and Feb 29`() {
            val p = parse("Anniversary every mar 3")
            assertEquals(Freq.YEARLY, p.recurrence!!.freq)
            assertEquals(3, p.recurrence!!.byMonth)
            assertEquals(d("2027-03-03"), p.dueDate)
            val leap = parse("Leap check every feb 29")
            assertEquals(d("2027-02-28"), leap.dueDate)
        }

        @Test
        fun `every 31st starting in a short month`() {
            val ctx = CTX.copy(now = LocalDateTime.parse("2027-04-03T09:00"))
            val p = parse("Close books every 31st", ctx)
            assertEquals(d("2027-04-30"), p.dueDate)
            assertEquals(d("2027-05-31"), p.recurrence!!.firstAfter(p.dueDate!!))
        }

        @Test
        fun `after completion forms`() {
            val a = parse("Water plants 3 days after completion")
            assertEquals(RepeatMode.AFTER_COMPLETION, a.recurrence!!.mode)
            assertEquals(3, a.recurrence!!.interval)
            assertEquals("Water plants", a.title)
            val b = rule("Haircut every! 6 weeks")
            assertEquals(RepeatMode.AFTER_COMPLETION, b.mode)
            assertEquals(Freq.WEEKLY, b.freq)
            val c = rule("Descale every 2 months after completion")
            assertEquals(RepeatMode.AFTER_COMPLETION, c.mode)
            assertEquals(Freq.MONTHLY, c.freq)
        }

        @Test
        fun `daily, weekly, monthly words`() {
            assertEquals(Freq.DAILY, rule("Journal daily").freq)
            assertEquals(Freq.MONTHLY, rule("Backup monthly").freq)
            assertEquals(setOf(DayOfWeek.FRIDAY), rule("Review weekly on fri").byWeekdays)
            assertEquals(2, rule("Clean fortnightly").interval)
        }

        @Test
        fun `every morning`() {
            val p = parse("Meditate every morning")
            assertEquals(Freq.DAILY, p.recurrence!!.freq)
            assertEquals(t("09:00"), p.dueTime)
            assertEquals(d("2026-11-20"), p.dueDate)
        }

        @Test
        fun `every with a start date`() {
            val p = parse("Lessons every week jan 5")
            assertEquals(d("2027-01-05"), p.dueDate)
            assertEquals(DayOfWeek.TUESDAY, p.recurrence!!.anchor.dayOfWeek)
        }

        @Test
        fun `'every' alone is just a word`() {
            val p = parse("Read every chapter")
            assertNull(p.recurrence)
            assertEquals("Read every chapter", p.title)
        }

        @Test
        fun `description round trip`() {
            for (text in listOf("every weekday", "every 2 weeks on monday", "every month on the 1st", "every month on the last friday", "3 days after completion")) {
                val r = rule("x $text")
                val again = rule("x " + r.describe().lowercase())
                assertEquals(r.copy(anchor = again.anchor), again, text)
            }
        }
    }

    @Nested
    inner class Reminders {
        @Test
        fun `bang and phrase forms`() {
            val p = parse("Flight tomorrow 9am !2h remind me 30 min before")
            assertEquals(listOf(ReminderSpec.Before(120), ReminderSpec.Before(30)), p.reminders)
            assertEquals("Flight", p.title)
        }

        @Test
        fun `exclamation in a title is not a reminder`() {
            assertTrue(parse("Celebrate!1").reminders.isEmpty())
        }
    }

    @Nested
    inner class Corrections {
        @Test
        fun `quoted text is never parsed`() {
            val p = parse("Watch \"Friday Night Lights\" tomorrow")
            assertEquals(d("2026-11-20"), p.dueDate)
            assertEquals("Watch \"Friday Night Lights\"", p.title)
        }

        @Test
        fun `a dismissed chip turns back into title text`() {
            val first = parse("Read the 1st chapter")
            assertEquals(d("2026-12-01"), first.dueDate)
            val span = first.spans.single { it.kind == TokenKind.DATE }
            val again = parse("Read the 1st chapter", CTX.copy(ignore = setOf(span.kind to span.text)))
            assertNull(again.dueDate)
            assertEquals("Read the 1st chapter", again.title)
        }

        @Test
        fun `a dismissed project is kept as text`() {
            val p = parse("Learn C #Work", CTX.copy(ignore = setOf(TokenKind.PROJECT to "#Work")))
            assertNull(p.project)
            assertEquals("Learn C #Work", p.title)
        }
    }

    @Test
    fun `unicode titles survive`() {
        val p = parse("Café with Zoë 🌿 tomorrow @errands")
        assertEquals("Café with Zoë 🌿", p.title)
        assertEquals(d("2026-11-20"), p.dueDate)
    }

    @Test
    fun `spans point at the right characters`() {
        val text = "Buy flowers fri 6pm #Home Renovation @errands p1"
        val p = parse(text)
        for (s in p.spans) assertEquals(s.text, text.substring(s.start, s.end))
        assertEquals(5, p.spans.size)
    }
}
