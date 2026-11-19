package cc.toolcrate.telos.domain.quickadd

import java.time.DayOfWeek
import java.time.Month

/** English words the parser understands. Table-driven so other languages can be added. */
internal object Vocabulary {
    val weekdaysFull: Map<String, DayOfWeek> = DayOfWeek.entries.associateBy { it.name.lowercase() }

    val weekdaysShort: Map<String, DayOfWeek> = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )

    val weekdays: Map<String, DayOfWeek> = weekdaysFull + weekdaysShort

    val months: Map<String, Month> = Month.entries.associateBy { it.name.lowercase() } + mapOf(
        "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mar" to Month.MARCH, "apr" to Month.APRIL,
        "jun" to Month.JUNE, "jul" to Month.JULY, "aug" to Month.AUGUST, "sep" to Month.SEPTEMBER,
        "sept" to Month.SEPTEMBER, "oct" to Month.OCTOBER, "nov" to Month.NOVEMBER, "dec" to Month.DECEMBER,
    )

    val numbers: Map<String, Int> = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "other" to 2,
    )

    val ordinals: Map<String, Int> = mapOf(
        "first" to 1, "1st" to 1, "second" to 2, "2nd" to 2, "third" to 3, "3rd" to 3,
        "fourth" to 4, "4th" to 4, "fifth" to 5, "5th" to 5, "last" to -1,
    )

    /** Regex alternation, longest first so "thursday" wins over "thu". */
    fun alternation(words: Collection<String>): String =
        words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    val weekdayAlt = alternation(weekdays.keys)
    val weekdayFullAlt = alternation(weekdaysFull.keys)
    val monthAlt = alternation(months.keys)
}
