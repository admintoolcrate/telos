package cc.toolcrate.telos.ui

import cc.toolcrate.telos.domain.time.WallTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Calm, relative date labels: "Today", "Tomorrow", "Fri", "3 Dec", "3 Dec 2027". */
object Format {
    fun day(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val diff = date.toEpochDay() - today.toEpochDay()
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff == -1L -> "Yesterday"
            diff in 2..6 -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
            else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
        }
    }

    /** Full date for headings and TalkBack: "Thursday 19 November". */
    fun longDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern(if (date.year == LocalDate.now().year) "EEEE d MMMM" else "EEEE d MMMM yyyy", locale))

    fun time(minuteOfDay: Int, locale: Locale = Locale.getDefault()): String =
        WallTime.timeOf(minuteOfDay).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    fun due(epochDay: Long?, minute: Int?, today: LocalDate): String? {
        epochDay ?: return null
        val d = day(LocalDate.ofEpochDay(epochDay), today)
        return if (minute != null) "$d ${time(minute)}" else d
    }

    fun priorityName(p: Int) = when (p) {
        1 -> "Priority 1"
        2 -> "Priority 2"
        3 -> "Priority 3"
        else -> "No priority"
    }
}
