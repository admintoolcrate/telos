package cc.toolcrate.telos.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class RepeatMode {
    /** "every …": occurrences fall on a fixed schedule, whenever the task is completed. */
    SCHEDULE,

    /** "every! …" / "3 days after completion": the next one is counted from the completion. */
    AFTER_COMPLETION,
}

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** "The 2nd Tuesday" (ordinal 2) or "the last Friday" (ordinal -1). */
data class WeekdayOrdinal(val ordinal: Int, val day: DayOfWeek) {
    init {
        require(ordinal in -5..5 && ordinal != 0) { "Ordinal out of range: $ordinal" }
    }
}

/**
 * A repeat rule. Dates only: a task's time of day stays on the task and is carried over to
 * each occurrence, so the rule never deals with DST (see [cc.toolcrate.telos.domain.time.WallTime]).
 *
 * Occurrences never fall before [anchor]. Periods ("every 2 weeks", "every 3 months") are
 * counted from the anchor's period, so the rhythm survives late completions.
 */
data class Recurrence(
    val freq: Freq,
    val interval: Int = 1,
    val mode: RepeatMode = RepeatMode.SCHEDULE,
    /** WEEKLY: the days in each week (empty = the anchor's weekday). */
    val byWeekdays: Set<DayOfWeek> = emptySet(),
    /** MONTHLY/YEARLY: days of the month; negative counts from the end (-1 = last day). */
    val byMonthDays: List<Int> = emptyList(),
    /** MONTHLY/YEARLY: "the 2nd Tuesday", instead of [byMonthDays]. */
    val ordinal: WeekdayOrdinal? = null,
    /** YEARLY: the month (1..12); null = the anchor's month. */
    val byMonth: Int? = null,
    val anchor: LocalDate,
    /** No occurrences after this date. */
    val until: LocalDate? = null,
    /** Occurrences still to come, including the current one; null = unlimited. */
    val remaining: Int? = null,
) {
    init {
        require(interval in 1..999) { "Interval out of range: $interval" }
        require(byMonthDays.all { it in -31..31 && it != 0 }) { "Bad month day in $byMonthDays" }
        require(byMonth == null || byMonth in 1..12) { "Bad month $byMonth" }
        require(remaining == null || remaining >= 0)
    }

    /** The first occurrence on or after [date], or null if the rule has ended. */
    fun firstOnOrAfter(date: LocalDate): LocalDate? {
        val from = maxOf(date, anchor)
        var period = periodIndex(from)
        // A bounded search: a valid rule always has an occurrence within a few periods; the bound
        // guards against a rule that can never match (e.g. "the 5th Monday" every 12 months in
        // a month that never has one).
        repeat(MAX_PERIODS) {
            val hit = datesInPeriod(period).firstOrNull { it >= from }
            if (hit != null) return hit.takeIf { until == null || it <= until }
            period += interval
            if (until != null && periodStart(period) > until) return null
        }
        return null
    }

    /** The first occurrence strictly after [date]. */
    fun firstAfter(date: LocalDate): LocalDate? = firstOnOrAfter(date.plusDays(1))

    /** Occurrences from [from] onwards, in order. */
    fun occurrences(from: LocalDate = anchor): Sequence<LocalDate> =
        generateSequence(firstOnOrAfter(from)) { firstAfter(it) }

    /** Whether [date] is an occurrence. */
    fun matches(date: LocalDate): Boolean = firstOnOrAfter(date) == date

    /**
     * When a task with this rule, due on [due], is completed on [completedOn]: the next due date
     * and the rule to store with it (with [remaining] counted down), or null if it was the last.
     *
     * SCHEDULE: the first occurrence after the current due date that is also on or after today,
     * so an overdue daily task lands on today instead of leaving a backlog behind.
     * AFTER_COMPLETION: [interval] units after the completion date.
     */
    fun advance(due: LocalDate?, completedOn: LocalDate): Advance? {
        if (remaining != null && remaining <= 1) return null
        val next = when (mode) {
            RepeatMode.SCHEDULE -> {
                val after = due ?: completedOn.minusDays(1)
                firstOnOrAfter(maxOf(after.plusDays(1), completedOn))
            }
            RepeatMode.AFTER_COMPLETION -> afterCompletion(completedOn)
        } ?: return null
        if (until != null && next > until) return null
        return Advance(next, copy(remaining = remaining?.minus(1)))
    }

    data class Advance(val due: LocalDate, val rule: Recurrence)

    private fun afterCompletion(completedOn: LocalDate): LocalDate {
        val base = when (freq) {
            Freq.DAILY -> completedOn.plusDays(interval.toLong())
            Freq.WEEKLY -> completedOn.plusWeeks(interval.toLong())
            // plusMonths/plusYears clamp to the month's end (Jan 31 + 1 month = Feb 28/29).
            Freq.MONTHLY -> completedOn.plusMonths(interval.toLong())
            Freq.YEARLY -> completedOn.plusYears(interval.toLong())
        }
        // "every! week on Monday": the first listed weekday on or after that point.
        return if (freq == Freq.WEEKLY && byWeekdays.isNotEmpty()) {
            generateSequence(base) { it.plusDays(1) }.first { it.dayOfWeek in byWeekdays }
        } else {
            base
        }
    }

    // --- Periods: day, ISO week (Monday start), month or year, numbered from the anchor's. ---

    private fun periodIndex(date: LocalDate): Long {
        val raw = when (freq) {
            Freq.DAILY -> ChronoUnit.DAYS.between(anchor, date)
            Freq.WEEKLY -> ChronoUnit.WEEKS.between(weekStart(anchor), weekStart(date))
            Freq.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(date))
            Freq.YEARLY -> (date.year - anchor.year).toLong()
        }
        // Round down to a period the interval lands on.
        return raw - Math.floorMod(raw, interval.toLong())
    }

    private fun periodStart(index: Long): LocalDate = when (freq) {
        Freq.DAILY -> anchor.plusDays(index)
        Freq.WEEKLY -> weekStart(anchor).plusWeeks(index)
        Freq.MONTHLY -> YearMonth.from(anchor).plusMonths(index).atDay(1)
        Freq.YEARLY -> LocalDate.of(anchor.year + index.toInt(), 1, 1)
    }

    private fun datesInPeriod(index: Long): List<LocalDate> = when (freq) {
        Freq.DAILY -> listOf(periodStart(index))
        Freq.WEEKLY -> {
            val start = periodStart(index)
            val days = byWeekdays.ifEmpty { setOf(anchor.dayOfWeek) }
            days.map { start.plusDays((it.value - 1).toLong()) }.sorted()
        }
        Freq.MONTHLY -> monthDates(YearMonth.from(anchor).plusMonths(index))
        Freq.YEARLY -> {
            val month = Month.of(byMonth ?: anchor.monthValue)
            monthDates(YearMonth.of(anchor.year + index.toInt(), month))
        }
    }

    private fun monthDates(month: YearMonth): List<LocalDate> {
        ordinal?.let { o ->
            val date = if (o.ordinal > 0) {
                month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(o.ordinal, o.day))
            } else {
                month.atEndOfMonth().with(TemporalAdjusters.dayOfWeekInMonth(o.ordinal, o.day))
            }
            // "The 5th Monday" doesn't exist in every month; java.time would roll into the next.
            return if (YearMonth.from(date) == month) listOf(date) else emptyList()
        }
        val days = byMonthDays.ifEmpty { listOf(anchor.dayOfMonth) }
        val length = month.lengthOfMonth()
        return days.map { d ->
            // Past the end of a short month: the month's last day (the 31st → Feb 28/29, Apr 30).
            val day = if (d > 0) minOf(d, length) else maxOf(1, length + 1 + d)
            month.atDay(day)
        }.distinct().sorted()
    }

    companion object {
        private const val MAX_PERIODS = 2000

        private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
