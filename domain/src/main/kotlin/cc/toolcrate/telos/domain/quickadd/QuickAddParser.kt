package cc.toolcrate.telos.domain.quickadd

import cc.toolcrate.telos.domain.model.ReminderSpec
import cc.toolcrate.telos.domain.quickadd.Vocabulary.monthAlt
import cc.toolcrate.telos.domain.quickadd.Vocabulary.months
import cc.toolcrate.telos.domain.quickadd.Vocabulary.numbers
import cc.toolcrate.telos.domain.quickadd.Vocabulary.weekdayAlt
import cc.toolcrate.telos.domain.quickadd.Vocabulary.weekdays
import cc.toolcrate.telos.domain.quickadd.Vocabulary.weekdaysShort
import cc.toolcrate.telos.domain.recurrence.Freq
import cc.toolcrate.telos.domain.recurrence.Recurrence
import cc.toolcrate.telos.domain.recurrence.RepeatMode
import cc.toolcrate.telos.domain.recurrence.WeekdayOrdinal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Natural-language quick add, entirely on the device:
 * `Call dentist tomorrow 3pm #Health @phone p2` → title "Call dentist", tomorrow at 15:00,
 * project Health, label phone, priority 2.
 *
 * Works in passes; each pass claims the characters it recognises, and later passes skip claimed
 * text. Order: quoted text (protected), #project, /section, ^goal, @labels, p1–p4, !reminders,
 * recurrence, date, time. Whatever is left is the title.
 */
object QuickAddParser {
    private const val B = """(?<![\p{L}\p{N}_])"""
    private const val E = """(?![\p{L}\p{N}_])"""
    private const val ON = """(?:(?:on|by|due|for)\s+)?"""
    private const val NUM = """(\d{1,3}|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"""
    private val opts = setOf(RegexOption.IGNORE_CASE)

    fun parse(input: String, ctx: ParseContext): ParsedTask {
        val s = State(input, ctx)
        s.protectQuotes()

        val project = s.named('#', TokenKind.PROJECT, ctx.projects.map { it.id to it.name })
        val projectId = (project as? Ref.Existing)?.id ?: if (project == null) ctx.defaultProjectId else null
        val section = s.named(
            '/', TokenKind.SECTION,
            ctx.sections.filter { it.projectId == projectId }.map { it.id to it.name },
            allowNew = projectId != null || project is Ref.New,
        )
        val goal = s.named('^', TokenKind.GOAL, ctx.goals.map { it.id to it.name })
        val labels = buildList {
            while (true) add(s.named('@', TokenKind.LABEL, ctx.labels.map { it to it }) ?: break)
        }.distinctBy { it.name.lowercase() }.map { if (it is Ref.Existing) Ref.Existing(it.name, it.name) else it }

        val priority = s.claim(Regex("${B}p([1-4])$E", opts), TokenKind.PRIORITY) { it.groupValues[1].toInt() }
        val reminders = buildList {
            while (true) add(s.claim(reminderRegex, TokenKind.REMINDER) { m -> minutes(m.groupValues[1], m.groupValues[2]) } ?: break)
            while (true) add(s.claim(remindPhrase, TokenKind.REMINDER) { m -> minutes(m.groupValues[1], m.groupValues[2]) } ?: break)
        }.map { ReminderSpec.Before(it) }

        val rule = parseRecurrence(s)
        val date = parseDate(s, ctx)
        val time = parseTime(s, ctx) ?: date?.time ?: rule?.time

        var due: LocalDate? = date?.date
        if (due == null && time != null && rule == null) {
            due = if (time.date != null) time.date else if (time.time > ctx.now.toLocalTime()) ctx.today else ctx.today.plusDays(1)
        }
        if (due == null && time?.date != null) due = time.date

        var recurrence: Recurrence? = null
        if (rule != null) {
            val start = due ?: ctx.today
            // The first occurrence is the first matching day from the start; the rhythm
            // ("every other week") is then counted from there.
            val draft = rule.build(anchor = start).copy(interval = 1)
            var first = if (rule.mode == RepeatMode.SCHEDULE) draft.firstOnOrAfter(start) ?: start else start
            if (due == null && time != null && first == ctx.today && time.time <= ctx.now.toLocalTime() && rule.mode == RepeatMode.SCHEDULE) {
                first = draft.firstAfter(first) ?: first
            }
            due = first
            recurrence = rule.build(anchor = first)
        }

        return ParsedTask(
            title = s.remainingTitle(),
            dueDate = due,
            dueTime = time?.time,
            recurrence = recurrence,
            project = project,
            section = section,
            labels = labels,
            priority = priority,
            goal = goal,
            reminders = reminders,
            spans = s.spans.sortedBy { it.start },
        )
    }

    // --- Reminders ---------------------------------------------------------------------------

    private val unitAlt = """m|min|mins|minutes?|h|hrs?|hours?|d|days?"""
    private val reminderRegex = Regex("""(?<!\S)!(\d{1,4})\s?($unitAlt)$E""", opts)
    private val remindPhrase = Regex("""${B}remind(?:\s+me)?\s+(\d{1,4})\s?($unitAlt)\s+before$E""", opts)

    private fun minutes(n: String, unit: String): Int {
        val v = n.toInt()
        return when (unit.lowercase().first()) {
            'h' -> v * 60
            'd' -> v * 24 * 60
            else -> v
        }
    }

    // --- Recurrence --------------------------------------------------------------------------

    private class RuleSpec(
        val freq: Freq,
        val interval: Int = 1,
        val byWeekdays: Set<DayOfWeek> = emptySet(),
        val byMonthDays: List<Int> = emptyList(),
        val ordinal: WeekdayOrdinal? = null,
        val byMonth: Int? = null,
        val mode: RepeatMode = RepeatMode.SCHEDULE,
        val time: TimeHit? = null,
    ) {
        fun build(anchor: LocalDate) = Recurrence(
            freq = freq, interval = interval, mode = mode, byWeekdays = byWeekdays, byMonthDays = byMonthDays,
            ordinal = ordinal, byMonth = byMonth, anchor = anchor,
        )

        fun with(mode: RepeatMode) = RuleSpec(freq, interval, byWeekdays, byMonthDays, ordinal, byMonth, mode, time)
    }

    private val ruleStart = Regex("""${B}(every!?|daily|weekly|fortnightly|monthly|yearly|annually)(?![\p{L}\p{N}_])""", opts)
    private val afterCompletion = Regex(
        """$B$NUM\s+(days?|weeks?|months?|years?)\s+after\s+(?:completion|completing|done)$E""", opts,
    )
    private val trailingAfter = Regex("""^\s+after\s+(?:completion|completing|done)$E""", opts)
    private val word = Regex("""[^\s,]+""")

    private fun parseRecurrence(s: State): RuleSpec? {
        // "3 days after completion"
        s.findFree(afterCompletion, TokenKind.RECURRENCE)?.let { m ->
            val n = number(m.groupValues[1]) ?: return@let
            val freq = unitFreq(m.groupValues[2]) ?: return@let
            s.take(m.range.first, m.range.last + 1, TokenKind.RECURRENCE)
            return RuleSpec(freq, n, mode = RepeatMode.AFTER_COMPLETION)
        }
        for (m in ruleStart.findAll(s.text)) {
            if (!s.isFree(m.range.first, m.range.last + 1)) continue
            val head = m.groupValues[1].lowercase()
            var end = m.range.last + 1
            val spec: RuleSpec = when (head) {
                "daily" -> RuleSpec(Freq.DAILY)
                "fortnightly" -> RuleSpec(Freq.WEEKLY, 2)
                "monthly" -> RuleSpec(Freq.MONTHLY)
                "yearly", "annually" -> RuleSpec(Freq.YEARLY)
                "weekly" -> {
                    // "weekly on mon" reads like "every week on mon".
                    val tail = everyBody(s, end, "week")
                    if (tail != null) { end = tail.second; tail.first } else RuleSpec(Freq.WEEKLY)
                }
                else -> {
                    val body = everyBody(s, end, null) ?: continue
                    end = body.second
                    body.first.let { if (head == "every!") it.with(RepeatMode.AFTER_COMPLETION) else it }
                }
            }
            var result = spec
            trailingAfter.find(s.text.substring(end))?.let {
                end += it.range.last + 1
                result = result.with(RepeatMode.AFTER_COMPLETION)
            }
            if (s.isIgnored(TokenKind.RECURRENCE, s.text.substring(m.range.first, end))) continue
            s.take(m.range.first, end, TokenKind.RECURRENCE)
            return result
        }
        return null
    }

    /**
     * The words after "every". [preset] stands in for an already-read unit ("weekly" = "every week").
     * Returns the rule and the end offset of what it read.
     */
    private fun everyBody(s: State, from: Int, preset: String?): Pair<RuleSpec, Int>? {
        val words = word.findAll(s.text, from).takeWhile { s.isFree(it.range.first, it.range.last + 1) }.take(12).toList()
        var i = 0
        fun w(k: Int = i) = words.getOrNull(k)?.value?.lowercase()?.trimEnd('.', ';')
        fun endOf(k: Int) = words[k].range.last + 1 + if (s.text.getOrNull(words[k].range.last + 1) == ',') 1 else 0
        fun skip(vararg ws: String) { while (w() in ws) i++ }

        var interval = 1
        if (preset == null) {
            val n = w()?.let(::number)
            if (n != null && w() !in setOf("a", "an")) {
                val next = w(i + 1)
                if (next != null && (unitFreq(next) != null)) { interval = n; i++ }
            }
        }
        val unit = preset ?: w() ?: return null
        val spec: RuleSpec
        var last: Int // index of the last word read; -1 when only the preset was read
        when {
            preset != null || unitFreq(unit) != null -> {
                val freq = unitFreq(unit)!!
                last = if (preset == null) i else -1
                if (preset == null) i++
                spec = when (freq) {
                    Freq.WEEKLY -> {
                        skip("on")
                        val days = weekdayList(words, i, ::w)
                        if (days != null) { last = days.second; i = days.second + 1; RuleSpec(Freq.WEEKLY, interval, byWeekdays = days.first) }
                        else RuleSpec(Freq.WEEKLY, interval)
                    }
                    Freq.MONTHLY -> {
                        val save = i
                        skip("on", "the")
                        val day = monthDayPart(words, i, ::w)
                        if (day != null) { last = day.second; RuleSpec(Freq.MONTHLY, interval, byMonthDays = day.first.days, ordinal = day.first.ordinal) }
                        else { i = save; RuleSpec(Freq.MONTHLY, interval) }
                    }
                    Freq.YEARLY -> {
                        val save = i
                        skip("on", "the")
                        val md = monthAndDay(words, i, ::w)
                        if (md != null) { last = md.third; RuleSpec(Freq.YEARLY, interval, byMonth = md.first, byMonthDays = listOf(md.second)) }
                        else { i = save; RuleSpec(Freq.YEARLY, interval) }
                    }
                    Freq.DAILY -> RuleSpec(Freq.DAILY, interval)
                }
            }
            unit in setOf("weekday", "weekdays", "workday", "workdays") -> {
                last = i; spec = RuleSpec(Freq.WEEKLY, byWeekdays = Recurrence.WEEKDAYS)
            }
            unit in setOf("weekend", "weekends") -> {
                last = i; spec = RuleSpec(Freq.WEEKLY, byWeekdays = Recurrence.WEEKEND)
            }
            unit in setOf("morning", "mornings") -> { last = i; spec = RuleSpec(Freq.DAILY, time = TimeHit(LocalTime.of(9, 0))) }
            unit in setOf("afternoon", "afternoons") -> { last = i; spec = RuleSpec(Freq.DAILY, time = TimeHit(LocalTime.of(15, 0))) }
            unit in setOf("evening", "evenings") -> { last = i; spec = RuleSpec(Freq.DAILY, time = TimeHit(LocalTime.of(19, 0))) }
            unit in setOf("night", "nights") -> { last = i; spec = RuleSpec(Freq.DAILY, time = TimeHit(LocalTime.of(21, 0))) }
            weekdays.containsKey(unit) -> {
                val days = weekdayList(words, i, ::w) ?: return null
                last = days.second
                spec = RuleSpec(Freq.WEEKLY, interval, byWeekdays = days.first)
            }
            else -> {
                skip("the")
                monthAndDay(words, i, ::w)?.let { md ->
                    return RuleSpec(Freq.YEARLY, interval, byMonth = md.first, byMonthDays = listOf(md.second)) to endOf(md.third)
                }
                val day = monthDayPart(words, i, ::w) ?: return null
                last = day.second
                spec = RuleSpec(Freq.MONTHLY, interval, byMonthDays = day.first.days, ordinal = day.first.ordinal)
            }
        }
        return spec to if (last < 0) from else endOf(last)
    }

    private class MonthDay(val days: List<Int> = emptyList(), val ordinal: WeekdayOrdinal? = null)

    /** "1st", "1st and 15th", "last day", "2nd tuesday", "last fri". Returns the last word index. */
    private fun monthDayPart(words: List<MatchResult>, start: Int, w: (Int) -> String?): Pair<MonthDay, Int>? {
        val first = w(start) ?: return null
        val ord = Vocabulary.ordinals[first]
        val next = w(start + 1)
        if (ord != null && next != null && weekdays.containsKey(next)) {
            return MonthDay(ordinal = WeekdayOrdinal(ord, weekdays.getValue(next))) to start + 1
        }
        if (first == "last" && next == "day") return MonthDay(days = listOf(-1)) to start + 1
        val days = mutableListOf<Int>()
        var i = start
        var lastIdx = -1
        while (true) {
            val d = dayOrdinal(w(i) ?: break) ?: break
            days += d
            lastIdx = i
            if (w(i + 1) in setOf("and", "&") && dayOrdinal(w(i + 2) ?: "") != null) i += 2 else break
        }
        return if (days.isEmpty()) null else MonthDay(days = days) to lastIdx
    }

    /** "jan 5", "5 jan", "5th of january": month (1..12), day, last word index. */
    private fun monthAndDay(words: List<MatchResult>, start: Int, w: (Int) -> String?): Triple<Int, Int, Int>? {
        val a = w(start) ?: return null
        val b = w(start + 1) ?: return null
        months[a]?.let { m -> dayNumber(b)?.let { return Triple(m.value, it, start + 1) } }
        val d = dayNumber(a) ?: return null
        if (b == "of") { val c = w(start + 2) ?: return null; months[c]?.let { return Triple(it.value, d, start + 2) } }
        months[b]?.let { return Triple(it.value, d, start + 1) }
        return null
    }

    private fun weekdayList(words: List<MatchResult>, start: Int, w: (Int) -> String?): Pair<Set<DayOfWeek>, Int>? {
        val days = mutableSetOf<DayOfWeek>()
        var i = start
        var last = -1
        while (true) {
            val d = weekdays[w(i) ?: break] ?: break
            days += d
            last = i
            if (w(i + 1) in setOf("and", "&", "or") && weekdays.containsKey(w(i + 2) ?: "")) i += 2
            // "mon, wed" (the comma isn't part of the word) or "mon wed".
            else if (weekdays.containsKey(w(i + 1) ?: "")) i += 1
            else break
        }
        return if (days.isEmpty()) null else days to last
    }

    private fun dayOrdinal(w: String): Int? = Regex("""(\d{1,2})(st|nd|rd|th)""").matchEntire(w)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }
    private fun dayNumber(w: String): Int? = Regex("""(\d{1,2})(st|nd|rd|th)?""").matchEntire(w)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }

    private fun unitFreq(w: String): Freq? = when (w.lowercase().trimEnd('.')) {
        "day", "days" -> Freq.DAILY
        "week", "weeks" -> Freq.WEEKLY
        "month", "months" -> Freq.MONTHLY
        "year", "years" -> Freq.YEARLY
        else -> null
    }

    private fun number(w: String): Int? = w.toIntOrNull()?.takeIf { it in 1..999 } ?: numbers[w.lowercase()]

    // --- Dates -------------------------------------------------------------------------------

    private class DateHit(val date: LocalDate, val time: TimeHit? = null)

    private class Pattern<T>(val regex: Regex, val resolve: (MatchResult, State) -> T?)

    private val datePatterns: List<Pattern<DateHit>> = listOf(
        Pattern(Regex("""$B${ON}(?:the\s+)?day\s+after\s+tomorrow$E""", opts)) { _, s -> DateHit(s.ctx.today.plusDays(2)) },
        Pattern(Regex("""$B${ON}(today|tod)$E""", opts)) { _, s -> DateHit(s.ctx.today) },
        Pattern(Regex("""$B${ON}(tomorrow|tmrw|tmr|tom)$E""", opts)) { _, s -> DateHit(s.ctx.today.plusDays(1)) },
        Pattern(Regex("""${B}tonight$E""", opts)) { _, s ->
            // Tonight is 20:00, unless it's already later than that: then just today.
            val t = LocalTime.of(20, 0)
            DateHit(s.ctx.today, if (s.ctx.now.toLocalTime() < t) TimeHit(t) else null)
        },
        Pattern(Regex("""$B${ON}(?:(next|this)\s+)?($weekdayAlt)\.?$E""", opts)) { m, s ->
            val qualifier = m.groupValues[1].lowercase()
            val word = m.groupValues[2].lowercase()
            val hasPrefix = qualifier.isNotEmpty() || m.value.substring(0, m.value.length - m.groupValues[2].length).isNotBlank()
            // Short forms like "sun" or "sat" are common words; only take them when clearly dates.
            if (word in weekdaysShort && !hasPrefix && !s.atEndOrBeforeTime(m.range.last + 1)) return@Pattern null
            DateHit(weekdayDate(s.ctx, weekdays.getValue(word), qualifier))
        },
        Pattern(Regex("""${B}in\s+$NUM\s+(days?|weeks?|months?|years?)$E""", opts)) { m, s ->
            val n = number(m.groupValues[1]) ?: return@Pattern null
            DateHit(plus(s.ctx.today, n, unitFreq(m.groupValues[2])!!))
        },
        Pattern(Regex("""(?<!\S)\+(\d{1,3})\s?([dwmy])$E""", opts)) { m, s ->
            val f = when (m.groupValues[2].lowercase()) { "d" -> Freq.DAILY; "w" -> Freq.WEEKLY; "m" -> Freq.MONTHLY; else -> Freq.YEARLY }
            DateHit(plus(s.ctx.today, m.groupValues[1].toInt(), f))
        },
        Pattern(Regex("""$B${ON}next\s+(week|month|year)$E""", opts)) { m, s ->
            val t = s.ctx.today
            DateHit(
                when (m.groupValues[1].lowercase()) {
                    "week" -> t.with(TemporalAdjusters.previousOrSame(s.ctx.weekStart)).plusWeeks(1)
                    "month" -> t.withDayOfMonth(1).plusMonths(1)
                    else -> LocalDate.of(t.year + 1, 1, 1)
                }
            )
        },
        Pattern(Regex("""$B${ON}(?:the\s+)?(?:end\s+of\s+(?:the\s+)?(week|month)|(eow|eom))$E""", opts)) { m, s ->
            val unit = (m.groupValues[1].ifEmpty { m.groupValues[2] }).lowercase()
            val t = s.ctx.today
            DateHit(
                if (unit == "month" || unit == "eom") t.with(TemporalAdjusters.lastDayOfMonth())
                else t.with(TemporalAdjusters.previousOrSame(s.ctx.weekStart)).plusDays(6)
            )
        },
        Pattern(Regex("""$B${ON}(?:this\s+)?weekend$E""", opts)) { _, s ->
            val t = s.ctx.today
            DateHit(if (t.dayOfWeek == DayOfWeek.SUNDAY) t else t.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)))
        },
        Pattern(Regex("""$B${ON}($monthAlt)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?$E""", opts)) { m, s ->
            monthDate(s.ctx, months.getValue(m.groupValues[1].lowercase()).value, m.groupValues[2].toInt(), m.groupValues[3])
        },
        Pattern(Regex("""$B${ON}(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($monthAlt)\.?(?:,?\s+(\d{4}))?$E""", opts)) { m, s ->
            monthDate(s.ctx, months.getValue(m.groupValues[2].lowercase()).value, m.groupValues[1].toInt(), m.groupValues[3])
        },
        Pattern(Regex("""$B${ON}(\d{4})-(\d{2})-(\d{2})$E""", opts)) { m, _ ->
            runCatching { DateHit(LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())) }.getOrNull()
        },
        Pattern(Regex("""$B${ON}(\d{1,2})/(\d{1,2})(?:/(\d{2}|\d{4}))?(?![\p{L}\p{N}_/])""", opts)) { m, s ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val (day, month) = if (s.ctx.dayFirst) a to b else b to a
            if (month !in 1..12) return@Pattern null
            val year = m.groupValues[3].let { if (it.length == 2) "20$it" else it }
            monthDate(s.ctx, month, day, year)
        },
        Pattern(Regex("""$B${ON}the\s+(\d{1,2})(?:st|nd|rd|th)$E""", opts)) { m, s ->
            val day = m.groupValues[1].toInt()
            if (day !in 1..31) return@Pattern null
            // The next date with that day of the month, this month if it hasn't passed.
            var ym = YearMonth.from(s.ctx.today)
            repeat(13) {
                if (day <= ym.lengthOfMonth()) {
                    val d = ym.atDay(day)
                    if (d >= s.ctx.today) return@Pattern DateHit(d)
                }
                ym = ym.plusMonths(1)
            }
            null
        },
    )

    private fun parseDate(s: State, ctx: ParseContext): DateHit? = s.claimEarliest(datePatterns, TokenKind.DATE)

    private fun weekdayDate(ctx: ParseContext, day: DayOfWeek, qualifier: String): LocalDate {
        val t = ctx.today
        val weekStart = t.with(TemporalAdjusters.previousOrSame(ctx.weekStart))
        fun inWeek(start: LocalDate) = start.plusDays(Math.floorMod(day.value - ctx.weekStart.value, 7).toLong())
        return when (qualifier) {
            // "next fri": Friday in the following week.
            "next" -> inWeek(weekStart.plusWeeks(1))
            // "this fri": Friday this week; if that's gone, the coming one.
            "this" -> inWeek(weekStart).takeIf { it >= t } ?: t.with(TemporalAdjusters.next(day))
            // "fri": the coming Friday; today if today is Friday.
            else -> t.with(TemporalAdjusters.nextOrSame(day))
        }
    }

    private fun monthDate(ctx: ParseContext, month: Int, day: Int, year: String): DateHit? {
        if (year.isNotEmpty()) return runCatching { DateHit(LocalDate.of(year.toInt(), month, day)) }.getOrNull()
        // Without a year: this year, or next year if the date has passed (Feb 29 → the next leap year).
        for (y in ctx.today.year..ctx.today.year + 8) {
            val d = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: continue
            if (d >= ctx.today) return DateHit(d)
        }
        return null
    }

    private fun plus(d: LocalDate, n: Int, f: Freq): LocalDate = when (f) {
        Freq.DAILY -> d.plusDays(n.toLong())
        Freq.WEEKLY -> d.plusWeeks(n.toLong())
        Freq.MONTHLY -> d.plusMonths(n.toLong())
        Freq.YEARLY -> d.plusYears(n.toLong())
    }

    // --- Times -------------------------------------------------------------------------------

    /** A time of day; [date] is set when the time implies one ("in 2 hours" past midnight). */
    internal class TimeHit(val time: LocalTime, val date: LocalDate? = null)

    private val timePatterns: List<Pattern<TimeHit>> = listOf(
        Pattern(Regex("""$B(?:at\s+)?(\d{1,2})(?::([0-5]\d))?\s?(am|pm|a\.m\.|p\.m\.)(?![\p{L}\p{N}_])""", opts)) { m, _ ->
            val h = m.groupValues[1].toInt()
            if (h !in 1..12) return@Pattern null
            val pm = m.groupValues[3].lowercase().startsWith("p")
            val hour = (h % 12) + if (pm) 12 else 0
            TimeHit(LocalTime.of(hour, m.groupValues[2].ifEmpty { "0" }.toInt()))
        },
        Pattern(Regex("""$B(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)(?![\p{L}\p{N}_:.])""", opts)) { m, _ ->
            TimeHit(LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()))
        },
        // "18.30" or "18h30" only after "at": otherwise it's a version number or a price.
        Pattern(Regex("""${B}at\s+([01]?\d|2[0-3])[.h]([0-5]\d)(?![\p{L}\p{N}_:.])""", opts)) { m, _ ->
            TimeHit(LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()))
        },
        Pattern(Regex("""${B}at\s+(\d{1,2})(?![\p{L}\p{N}_:/.])""", opts)) { m, _ ->
            val h = m.groupValues[1].toInt()
            // "at 3" means the afternoon; "at 9" the morning.
            val hour = when (h) {
                in 1..7 -> h + 12
                in 8..23 -> h
                else -> return@Pattern null
            }
            TimeHit(LocalTime.of(hour, 0))
        },
        Pattern(Regex("""$B(?:at\s+)?(noon|midday|midnight)$E""", opts)) { m, _ ->
            TimeHit(if (m.groupValues[1].lowercase() == "midnight") LocalTime.MIDNIGHT else LocalTime.NOON)
        },
        Pattern(Regex("""$B(?:in\s+the\s+|this\s+)?(morning|afternoon|evening)$E""", opts)) { m, _ ->
            TimeHit(
                when (m.groupValues[1].lowercase()) {
                    "morning" -> LocalTime.of(9, 0)
                    "afternoon" -> LocalTime.of(15, 0)
                    else -> LocalTime.of(19, 0)
                }
            )
        },
        Pattern(Regex("""${B}in\s+$NUM\s+(min|mins|minutes?|hours?|hrs?)$E""", opts)) { m, s ->
            val n = number(m.groupValues[1]) ?: return@Pattern null
            val mins = if (m.groupValues[2].lowercase().startsWith("h")) n * 60L else n.toLong()
            // Rounded up to the next 5 minutes.
            val at = s.ctx.now.plusMinutes(mins).let { t ->
                val extra = (5 - t.minute % 5) % 5
                t.plusMinutes(extra.toLong()).withSecond(0).withNano(0)
            }
            TimeHit(at.toLocalTime(), at.toLocalDate())
        },
    )

    private fun parseTime(s: State, ctx: ParseContext): TimeHit? = s.claimEarliest(timePatterns, TokenKind.TIME)

    // --- State -------------------------------------------------------------------------------

    private class State(val text: String, val ctx: ParseContext) {
        val taken = BooleanArray(text.length)
        val protected = BooleanArray(text.length)
        val spans = mutableListOf<Span>()
        private val ignored = ctx.ignore.map { (k, t) -> k to t.trim().lowercase() }.toSet()

        fun protectQuotes() {
            Regex("\"[^\"]*\"|“[^”]*”").findAll(text).forEach { m -> for (i in m.range) protected[i] = true }
        }

        fun isFree(start: Int, end: Int): Boolean = (start until end).none { taken[it] || protected[it] }

        fun isIgnored(kind: TokenKind, matched: String) = (kind to matched.trim().lowercase()) in ignored

        fun take(start: Int, end: Int, kind: TokenKind) {
            for (i in start until end) taken[i] = true
            spans += Span(start, end, kind, text.substring(start, end))
        }

        fun findFree(regex: Regex, kind: TokenKind): MatchResult? =
            regex.findAll(text).firstOrNull { isFree(it.range.first, it.range.last + 1) && !isIgnored(kind, it.value) }

        fun <T> claim(regex: Regex, kind: TokenKind, value: (MatchResult) -> T?): T? {
            for (m in regex.findAll(text)) {
                if (!isFree(m.range.first, m.range.last + 1) || isIgnored(kind, m.value)) continue
                val v = value(m) ?: continue
                take(m.range.first, m.range.last + 1, kind)
                return v
            }
            return null
        }

        /** Of all patterns, the match that starts first (longest on a tie) wins. */
        fun <T> claimEarliest(patterns: List<Pattern<T>>, kind: TokenKind): T? {
            var best: Triple<MatchResult, T, Int>? = null
            for (p in patterns) {
                for (m in p.regex.findAll(text)) {
                    if (!isFree(m.range.first, m.range.last + 1) || isIgnored(kind, m.value)) continue
                    val v = p.resolve(m, this) ?: continue
                    val b = best
                    if (b == null || m.range.first < b.first.range.first ||
                        (m.range.first == b.first.range.first && m.value.length > b.first.value.length)
                    ) best = Triple(m, v, 0)
                    break
                }
            }
            val b = best ?: return null
            take(b.first.range.first, b.first.range.last + 1, kind)
            return b.second
        }

        /** True when only whitespace, claimed text or a time follows [pos]. */
        fun atEndOrBeforeTime(pos: Int): Boolean {
            val rest = buildString { for (i in pos until text.length) append(if (taken[i]) ' ' else text[i]) }.trimStart()
            return rest.isEmpty() || Regex("""^(?:at\s+\d|\d{1,2}(?::\d\d)?\s?(?:am|pm)|\d{1,2}:\d\d|noon|midnight)""", opts).containsMatchIn(rest)
        }

        /** `#Name`: longest existing name first (so names with spaces work), then `#"Quoted new"`, then one word. */
        fun named(sigil: Char, kind: TokenKind, existing: List<Pair<String, String>>, allowNew: Boolean = true): Ref? {
            var i = 0
            while (i < text.length) {
                if (text[i] == sigil && (i == 0 || text[i - 1].isWhitespace()) && !taken[i] && !protected[i]) {
                    val ref = refAt(i, kind, existing, allowNew)
                    if (ref != null) return ref
                }
                i++
            }
            return null
        }

        private fun refAt(at: Int, kind: TokenKind, existing: List<Pair<String, String>>, allowNew: Boolean): Ref? {
            val from = at + 1
            fun boundaryAt(p: Int) = p >= text.length || !(text[p].isLetterOrDigit() || text[p] == '_')
            for ((id, name) in existing.sortedByDescending { it.second.length }) {
                val end = from + name.length
                if (name.isNotEmpty() && text.regionMatches(from, name, 0, name.length, ignoreCase = true) && boundaryAt(end) &&
                    isFree(at, end) && !isIgnored(kind, text.substring(at, end))
                ) {
                    take(at, end, kind)
                    return Ref.Existing(id, name)
                }
            }
            if (!allowNew) return null
            if (text.getOrNull(from) == '"') {
                val close = text.indexOf('"', from + 1)
                if (close > from + 1) {
                    val raw = text.substring(at, close + 1)
                    if (isIgnored(kind, raw)) return null
                    for (k in at..close) { taken[k] = true; protected[k] = false }
                    spans += Span(at, close + 1, kind, raw)
                    return Ref.New(text.substring(from + 1, close).trim())
                }
            }
            val m = Regex("""[\p{L}\p{N}_\-]+""").matchAt(text, from) ?: return null
            val end = m.range.last + 1
            if (!isFree(at, end) || isIgnored(kind, text.substring(at, end))) return null
            take(at, end, kind)
            return Ref.New(m.value)
        }

        fun remainingTitle(): String {
            val sb = StringBuilder()
            for (i in text.indices) sb.append(if (taken[i]) ' ' else text[i])
            return sb.toString().replace(Regex("""\s+"""), " ").trim().trim(',', ';').trim()
                .replace(Regex("""\s+([,.;:!?])"""), "$1")
        }
    }
}
