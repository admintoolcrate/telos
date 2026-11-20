package cc.toolcrate.telos.domain.capture

import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.domain.views.TaskItem
import java.net.URI
import java.time.LocalDate

/** A task proposed from text shared by another app: the title goes to quick add, links to notes. */
data class Captured(val title: String, val notes: String)

/**
 * Turns shared text (and an optional subject) into a task. Nothing is fetched: a bare link
 * becomes "Read example.com/article" rather than its page title, so no request ever leaves.
 */
object SharedText {
    private val url = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    fun capture(text: String?, subject: String? = null): Captured {
        val body = text.orEmpty().trim()
        val links = url.findAll(body).map { it.value.trimEnd('.', ',', ')', ']', ';') }.toList()
        val rest = url.replace(body, " ").replace(Regex("\\s+"), " ").trim().trim('-', '–', '—', ':', '|').trim()
        val title = when {
            !subject.isNullOrBlank() -> subject.trim()
            rest.isNotEmpty() -> rest.lineSequence().first().take(200)
            links.isNotEmpty() -> "Read " + shortLink(links.first())
            else -> ""
        }
        val notes = buildList {
            if (links.isNotEmpty()) addAll(links)
            // Keep long shared text that didn't fit in the title.
            if (rest.length > 200 || rest.contains('\n')) add(rest)
            if (!subject.isNullOrBlank() && rest.isNotEmpty() && rest != subject.trim()) add(rest)
        }.distinct().joinToString("\n")
        return Captured(title.replace('\n', ' '), notes)
    }

    /** "https://www.example.com/a/b?x=1" → "example.com/a/b". */
    fun shortLink(link: String): String = runCatching {
        val u = URI(link)
        val host = u.host.orEmpty().removePrefix("www.")
        val path = u.path.orEmpty().trimEnd('/')
        (host + path).ifEmpty { link }
    }.getOrDefault(link).take(80)
}

/** What a widget row shows. Private tasks always show their cover title outside the app. */
data class WidgetRow(val id: String, val title: String, val meta: String?, val priority: Int, val earlier: Boolean)

object WidgetRows {
    fun today(index: Index, today: LocalDate, limit: Int = 30, timeText: (Int) -> String = { "%02d:%02d".format(it / 60, it % 60) }): List<WidgetRow> {
        val v = index.today(today)
        fun row(i: TaskItem, earlier: Boolean) = WidgetRow(
            id = i.id,
            title = i.task.coverTitle?.takeIf { it.isNotBlank() } ?: i.task.title,
            meta = i.task.dueMinute?.let(timeText),
            priority = i.task.priority,
            earlier = earlier,
        )
        return (v.earlier.map { row(it, true) } + v.today.map { row(it, false) }).take(limit)
    }
}
