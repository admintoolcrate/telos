package cc.toolcrate.telos.domain.recurrence

import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Plain-English text for a rule, as shown on a task ("Every 2 weeks on Monday"). */
fun Recurrence.describe(): String {
    val unit = when (freq) {
        Freq.DAILY -> "day"
        Freq.WEEKLY -> "week"
        Freq.MONTHLY -> "month"
        Freq.YEARLY -> "year"
    }
    val every = if (interval == 1) unit else "$interval ${unit}s"
    if (mode == RepeatMode.AFTER_COMPLETION) {
        val base = "$every after completion"
        return base.replaceFirstChar { it.uppercase() } + limits()
    }
    val text = when (freq) {
        Freq.DAILY -> "Every $every"
        Freq.WEEKLY -> when {
            interval == 1 && byWeekdays == Recurrence.WEEKDAYS -> "Every weekday"
            interval == 1 && byWeekdays == Recurrence.WEEKEND -> "Every weekend day"
            byWeekdays.isEmpty() -> "Every $every on ${anchor.dayOfWeek.long()}"
            byWeekdays.size == 1 -> "Every $every on ${byWeekdays.first().long()}"
            else -> "Every $every on " + byWeekdays.sorted().joinToString(", ") { it.short() }
        }
        Freq.MONTHLY -> "Every $every on the ${dayInMonth()}"
        Freq.YEARLY -> {
            val month = Month.of(byMonth ?: anchor.monthValue).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            when {
                ordinal != null -> "Every $every on the ${ordinalText(ordinal)} of $month"
                else -> "Every $every on ${byMonthDays.firstOrNull() ?: anchor.dayOfMonth} $month"
            }
        }
    }
    return text + limits()
}

private fun Recurrence.limits(): String = buildString {
    until?.let { append(", until $it") }
    remaining?.let { append(if (it == 1) ", once more" else ", $it more times") }
}

private fun Recurrence.dayInMonth(): String {
    ordinal?.let { return ordinalText(it) }
    val days = byMonthDays.ifEmpty { listOf(anchor.dayOfMonth) }
    return days.joinToString(" and ") { d ->
        when {
            d == -1 -> "last day"
            d < 0 -> "${nth(-d)} to last day"
            else -> nth(d)
        }
    }
}

private fun ordinalText(o: WeekdayOrdinal): String =
    (if (o.ordinal == -1) "last" else if (o.ordinal < 0) "${nth(-o.ordinal)} to last" else nth(o.ordinal)) + " " + o.day.long()

internal fun nth(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

private fun DayOfWeek.long() = getDisplayName(TextStyle.FULL, Locale.ENGLISH)
private fun DayOfWeek.short() = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
