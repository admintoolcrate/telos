package cc.toolcrate.telos.domain.quickadd

import cc.toolcrate.telos.domain.model.ReminderSpec
import cc.toolcrate.telos.domain.recurrence.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class TokenKind { DATE, TIME, RECURRENCE, PROJECT, SECTION, LABEL, PRIORITY, GOAL, REMINDER }

/** A recognised piece of the input, as a character range, for highlighting and chips. */
data class Span(val start: Int, val end: Int, val kind: TokenKind, val text: String)

/** Something to look up by name: an existing record, or a new one to create on save. */
sealed interface Ref {
    val name: String

    data class Existing(val id: String, override val name: String) : Ref
    data class New(override val name: String) : Ref
}

data class Named(val id: String, val name: String)
data class NamedSection(val id: String, val name: String, val projectId: String)

data class ParseContext(
    /** Local date and time on the device. */
    val now: LocalDateTime,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val projects: List<Named> = emptyList(),
    val sections: List<NamedSection> = emptyList(),
    val labels: List<String> = emptyList(),
    val goals: List<Named> = emptyList(),
    /** For numeric dates: 1/5 is 1 May when true (most of the world), 5 January when false (US). */
    val dayFirst: Boolean = true,
    /** Tokens the user dismissed (chip removed): matched text that must stay in the title. */
    val ignore: Set<Pair<TokenKind, String>> = emptySet(),
    /** Project to use when none is typed (for /section lookup). */
    val defaultProjectId: String? = null,
) {
    val today: LocalDate get() = now.toLocalDate()
}

data class ParsedTask(
    val title: String,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val recurrence: Recurrence? = null,
    val project: Ref? = null,
    val section: Ref? = null,
    val labels: List<Ref> = emptyList(),
    /** 1..4, or null when not given. */
    val priority: Int? = null,
    val goal: Ref? = null,
    val reminders: List<ReminderSpec.Before> = emptyList(),
    val spans: List<Span> = emptyList(),
)
