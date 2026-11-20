package cc.toolcrate.telos.domain.filter

import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.quickadd.ParseContext
import cc.toolcrate.telos.domain.quickadd.QuickAddParser
import cc.toolcrate.telos.domain.views.Index
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Where a query went wrong, for pointing at it in the editor. */
data class FilterError(val message: String, val position: Int)

/**
 * The saved-filter language:
 *
 *     expr  := or
 *     or    := and ('|' and)*
 *     and   := unary ('&' unary)*
 *     unary := '!' unary | '(' expr ')' | atom
 *
 * Atoms: `today`, `tomorrow`, `overdue`, `no date`, `no time`, `due: <date>`,
 * `due before: <date>`, `due after: <date>`, `next 7 days` (or `7 days`), `p1`–`p4`,
 * `no priority`, `@label` (`@home*` matches a prefix), `no labels`, `#project`, `##project`
 * (with subprojects), `/section`, `^goal`, `recurring`, `subtask`, `search: text`,
 * `created before: <date>`, `created after: <date>`, `all`.
 * Dates are anything quick add understands: `+3d`, `next friday`, `jan 5`, `2027-01-05`.
 */
sealed interface Expr {
    data class And(val a: Expr, val b: Expr) : Expr
    data class Or(val a: Expr, val b: Expr) : Expr
    data class Not(val e: Expr) : Expr
    data class Atom(val text: String, val test: (Task, EvalContext) -> Boolean) : Expr {
        override fun equals(other: Any?) = other is Atom && other.text == text
        override fun hashCode() = text.hashCode()
    }

    fun matches(t: Task, ctx: EvalContext): Boolean = when (this) {
        is And -> a.matches(t, ctx) && b.matches(t, ctx)
        is Or -> a.matches(t, ctx) || b.matches(t, ctx)
        is Not -> !e.matches(t, ctx)
        is Atom -> test(t, ctx)
    }
}

class EvalContext(val index: Index, val today: LocalDate, val zone: ZoneId) {
    val todayDay = today.toEpochDay()
    private val projectNames = index.projects.values.associate { it.id to it.name.lowercase() }

    fun projectNamed(id: String) = projectNames[id].orEmpty()

    /** [id] and every project nested under it. */
    fun withSubprojects(name: String): Set<String> {
        val roots = index.projects.values.filter { it.name.equals(name, ignoreCase = true) }.map { it.id }.toMutableSet()
        var grew = true
        while (grew) {
            val more = index.projects.values.filter { it.parentId in roots && it.id !in roots }.map { it.id }
            grew = more.isNotEmpty()
            roots += more
        }
        return roots
    }
}

object FilterQuery {
    fun parse(text: String, today: LocalDate = LocalDate.of(2000, 1, 1), weekStart: DayOfWeek = DayOfWeek.MONDAY): Result<Expr> =
        runCatching { Parser(text, today, weekStart).parseAll() }

    /** The open tasks [query] selects, in due order. */
    fun run(expr: Expr, ctx: EvalContext): List<Task> =
        ctx.index.openTasks.filter { expr.matches(it, ctx) }.sortedWith(Index.dueOrder)

    class ParseException(val error: FilterError) : Exception(error.message)

    private class Parser(val src: String, val today: LocalDate, val weekStart: DayOfWeek) {
        var pos = 0

        fun parseAll(): Expr {
            skipWs()
            if (pos >= src.length) fail("Type a query, for example: today & p1", 0)
            val e = or()
            skipWs()
            if (pos < src.length) fail(if (src[pos] == ')') "Unmatched ')'" else "Unexpected '${src[pos]}'")
            return e
        }

        fun or(): Expr {
            var left = and()
            while (peek('|')) { pos++; left = Expr.Or(left, and()) }
            return left
        }

        fun and(): Expr {
            var left = unary()
            while (peek('&')) { pos++; left = Expr.And(left, unary()) }
            return left
        }

        fun unary(): Expr {
            skipWs()
            if (pos >= src.length) fail("Something is missing at the end")
            return when (src[pos]) {
                '!' -> { pos++; Expr.Not(unary()) }
                '(' -> {
                    pos++
                    val e = or()
                    skipWs()
                    if (pos >= src.length || src[pos] != ')') fail("Missing ')'")
                    pos++
                    e
                }
                else -> atom()
            }
        }

        fun atom(): Expr {
            skipWs()
            val start = pos
            while (pos < src.length && src[pos] !in "&|()") pos++
            val raw = src.substring(start, pos).trim()
            if (raw.isEmpty()) fail("Expected a condition", start)
            return interpret(raw, start)
        }

        fun interpret(raw: String, at: Int): Expr.Atom {
            val t = raw.lowercase().replace(Regex("\\s+"), " ")
            fun atom(f: (Task, EvalContext) -> Boolean) = Expr.Atom(raw, f)
            Regex("p([1-4])").matchEntire(t)?.let { m -> val p = m.groupValues[1].toInt(); return atom { task, _ -> task.priority == p } }
            Regex("(?:next )?(\\d{1,3}) days").matchEntire(t)?.let { m ->
                val n = m.groupValues[1].toLong()
                return atom { task, c -> task.dueDate != null && task.dueDate >= c.todayDay && task.dueDate < c.todayDay + n }
            }
            when (t) {
                "all" -> return atom { _, _ -> true }
                "today" -> return atom { task, c -> task.dueDate == c.todayDay }
                "tomorrow" -> return atom { task, c -> task.dueDate == c.todayDay + 1 }
                "overdue", "od" -> return atom { task, c -> task.dueDate != null && task.dueDate < c.todayDay }
                "no date" -> return atom { task, _ -> task.dueDate == null }
                "no time" -> return atom { task, _ -> task.dueDate != null && task.dueMinute == null }
                "no priority" -> return atom { task, _ -> task.priority == 4 }
                "no labels", "no label" -> return atom { task, c -> c.index.labelsByTask[task.id].isNullOrEmpty() }
                "recurring" -> return atom { task, _ -> task.recurrence != null }
                "subtask", "subtasks" -> return atom { task, _ -> task.parentId != null }
                "no goal" -> return atom { task, c -> c.index.goalOf(task) == null }
            }
            when {
                t.startsWith("@") -> {
                    val name = t.drop(1).trim()
                    if (name.isEmpty()) fail("Which label?", at)
                    val prefix = name.endsWith("*")
                    val n = name.removeSuffix("*")
                    return atom { task, c ->
                        c.index.labelsByTask[task.id].orEmpty().any { l -> val ln = l.name.lowercase(); if (prefix) ln.startsWith(n) else ln == n }
                    }
                }
                t.startsWith("##") -> {
                    val name = raw.trim().drop(2).trim()
                    return atom { task, c -> task.projectId in c.withSubprojects(name) }
                }
                t.startsWith("#") -> {
                    val name = t.drop(1).trim()
                    if (name.isEmpty()) fail("Which project?", at)
                    return atom { task, c -> c.projectNamed(task.projectId) == name }
                }
                t.startsWith("/") -> {
                    val name = t.drop(1).trim()
                    return atom { task, c -> task.sectionId?.let { c.index.sections[it]?.name?.lowercase() } == name }
                }
                t.startsWith("^") -> {
                    val name = t.drop(1).trim()
                    return atom { task, c -> c.index.goalOf(task)?.let { c.index.goals[it]?.title?.lowercase() } == name }
                }
                t.startsWith("search:") -> {
                    val q = t.removePrefix("search:").trim()
                    return atom { task, _ -> task.title.lowercase().contains(q) || task.notes.lowercase().contains(q) }
                }
            }
            Regex("(due|created)( before| after)?: ?(.+)").matchEntire(t)?.let { m ->
                val field = m.groupValues[1]
                val op = m.groupValues[2].trim()
                val date = dateOf(m.groupValues[3], at + raw.lowercase().indexOf(m.groupValues[3]))
                return if (field == "due") {
                    when (op) {
                        "before" -> atom { task, c -> task.dueDate != null && task.dueDate < resolve(date, c) }
                        "after" -> atom { task, c -> task.dueDate != null && task.dueDate > resolve(date, c) }
                        else -> atom { task, c -> task.dueDate == resolve(date, c) }
                    }
                } else {
                    fun createdDay(task: Task, c: EvalContext) = LocalDate.ofInstant(Instant.ofEpochMilli(task.createdAt), c.zone).toEpochDay()
                    when (op) {
                        "before" -> atom { task, c -> createdDay(task, c) < resolve(date, c) }
                        "after" -> atom { task, c -> createdDay(task, c) > resolve(date, c) }
                        else -> atom { task, c -> createdDay(task, c) == resolve(date, c) }
                    }
                }
            }
            fail("Not understood: “$raw”", at)
        }

        /** A date relative to the day the filter is *run*, so "+3d" keeps meaning three days from now. */
        fun dateOf(text: String, at: Int): (LocalDate) -> Long {
            val trimmed = text.trim()
            Regex("([+-])(\\d{1,4}) ?d(?:ays?)?").matchEntire(trimmed)?.let { m ->
                val n = m.groupValues[2].toLong() * if (m.groupValues[1] == "-") -1 else 1
                return { day -> day.toEpochDay() + n }
            }
            if (trimmed == "yesterday") return { day -> day.toEpochDay() - 1 }
            // Anything quick add understands, worked out against the day it runs.
            val probe = parseDate(trimmed, today) ?: fail("Not a date: “$trimmed”", at)
            return { day -> parseDate(trimmed, day)?.toEpochDay() ?: probe.toEpochDay() }
        }

        fun parseDate(text: String, day: LocalDate): LocalDate? {
            val p = QuickAddParser.parse(text, ParseContext(now = day.atTime(LocalTime.NOON), weekStart = weekStart))
            return p.dueDate?.takeIf { p.title.isBlank() }
        }

        fun resolve(date: (LocalDate) -> Long, c: EvalContext) = date(c.today)

        fun peek(c: Char): Boolean { skipWs(); return pos < src.length && src[pos] == c }
        fun skipWs() { while (pos < src.length && src[pos].isWhitespace()) pos++ }
        fun fail(message: String, at: Int = pos): Nothing = throw ParseException(FilterError(message, at))
    }
}

/** The error from a failed [FilterQuery.parse], if that's what it was. */
fun Throwable.filterError(): FilterError? = (this as? FilterQuery.ParseException)?.error
