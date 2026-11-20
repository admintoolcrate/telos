package cc.toolcrate.telos.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Stores a [Recurrence] as a short RRULE-like string, stable across versions and readable in
 * an export: `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;MODE=SCHEDULE;ANCHOR=2026-11-19`.
 * Unknown keys are ignored, so older versions can read newer rules.
 */
object RecurrenceCodec {
    private val dayCodes = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
    )
    private val codeDays = dayCodes.entries.associate { (k, v) -> v to k }

    fun encode(r: Recurrence): String = buildList {
        add("FREQ=${r.freq.name}")
        if (r.interval != 1) add("INTERVAL=${r.interval}")
        if (r.byWeekdays.isNotEmpty()) add("BYDAY=" + r.byWeekdays.sorted().joinToString(",") { dayCodes.getValue(it) })
        if (r.byMonthDays.isNotEmpty()) add("BYMONTHDAY=" + r.byMonthDays.joinToString(","))
        r.ordinal?.let { add("ORD=${it.ordinal}${dayCodes.getValue(it.day)}") }
        r.byMonth?.let { add("BYMONTH=$it") }
        add("MODE=${r.mode.name}")
        add("ANCHOR=${r.anchor}")
        r.until?.let { add("UNTIL=$it") }
        r.remaining?.let { add("COUNT=$it") }
    }.joinToString(";")

    /** Null when [text] isn't a rule this version understands. */
    fun decode(text: String): Recurrence? = runCatching {
        val parts = text.split(';').filter { it.contains('=') }.associate {
            val (k, v) = it.split('=', limit = 2)
            k.trim().uppercase() to v.trim()
        }
        Recurrence(
            freq = Freq.valueOf(parts.getValue("FREQ")),
            interval = parts["INTERVAL"]?.toInt() ?: 1,
            mode = parts["MODE"]?.let { RepeatMode.valueOf(it) } ?: RepeatMode.SCHEDULE,
            byWeekdays = parts["BYDAY"]?.split(',')?.map { codeDays.getValue(it) }?.toSet() ?: emptySet(),
            byMonthDays = parts["BYMONTHDAY"]?.split(',')?.map { it.toInt() } ?: emptyList(),
            ordinal = parts["ORD"]?.let { WeekdayOrdinal(it.dropLast(2).toInt(), codeDays.getValue(it.takeLast(2))) },
            byMonth = parts["BYMONTH"]?.toInt(),
            anchor = LocalDate.parse(parts.getValue("ANCHOR")),
            until = parts["UNTIL"]?.let(LocalDate::parse),
            remaining = parts["COUNT"]?.toInt(),
        )
    }.getOrNull()
}
