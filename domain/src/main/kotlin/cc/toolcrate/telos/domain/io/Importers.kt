package cc.toolcrate.telos.domain.io

import cc.toolcrate.telos.domain.quickadd.ParseContext
import cc.toolcrate.telos.domain.quickadd.QuickAddParser
import cc.toolcrate.telos.domain.recurrence.Recurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A task as read from another format, before anything is saved. */
data class ImportedTask(
    val title: String,
    val notes: String = "",
    val priority: Int = 4,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val recurrence: Recurrence? = null,
    val labels: List<String> = emptyList(),
    val section: String? = null,
    val done: Boolean = false,
    val children: List<ImportedTask> = emptyList(),
) {
    fun count(): Int = 1 + children.sumOf { it.count() }
    fun flatten(): List<ImportedTask> = listOf(this) + children.flatMap { it.flatten() }
}

/** What an import would add: shown in the preview, saved only on confirm. */
data class ImportPlan(
    val source: String,
    val suggestedProject: String?,
    val sections: List<String>,
    val tasks: List<ImportedTask>,
    val warnings: List<String> = emptyList(),
    /** Todoist only: the file numbers priorities the old way round (4 = p1). */
    val prioritiesReversed: Boolean = false,
) {
    val taskCount: Int get() = tasks.sumOf { it.count() }
    val labels: Set<String> get() = tasks.flatMap { it.flatten() }.flatMap { it.labels }.toSet()
    val dated: Int get() = tasks.flatMap { it.flatten() }.count { it.dueDate != null }
    val recurring: Int get() = tasks.flatMap { it.flatten() }.count { it.recurrence != null }

    /** Swaps p1↔p4 and p2↔p3, for a file the detection got wrong. */
    fun flipPriorities(): ImportPlan {
        fun flip(t: ImportedTask): ImportedTask = t.copy(priority = 5 - t.priority, children = t.children.map(::flip))
        return copy(tasks = tasks.map(::flip), prioritiesReversed = !prioritiesReversed)
    }
}

/** Builds a tree from (depth, task) pairs in file order. */
private fun nest(items: List<Pair<Int, ImportedTask>>): List<ImportedTask> {
    data class Node(val task: ImportedTask, val depth: Int, val kids: MutableList<Node> = mutableListOf())
    val roots = mutableListOf<Node>()
    val stack = ArrayDeque<Node>()
    for ((depth, t) in items) {
        val node = Node(t, depth)
        while (stack.isNotEmpty() && stack.last().depth >= depth) stack.removeLast()
        if (stack.isEmpty()) roots += node else stack.last().kids += node
        stack.addLast(node)
    }
    fun build(n: Node): ImportedTask = n.task.copy(children = n.kids.map(::build))
    return roots.map(::build)
}

/**
 * Todoist's project export (CSV): TYPE (task / section / note), CONTENT, DESCRIPTION, PRIORITY,
 * INDENT, …, DATE (natural language, read with the quick-add parser). Notes become task notes;
 * @labels in the content become labels.
 */
object TodoistCsv {
    fun looksLike(text: String): Boolean = text.trimStart('﻿').lineSequence().firstOrNull()?.uppercase()?.let { "TYPE" in it && "CONTENT" in it } == true

    fun read(text: String, now: LocalDateTime, projectName: String? = null): ImportPlan {
        val rows = Csv.parse(text).filter { r -> r.any { it.isNotBlank() } }
        if (rows.isEmpty()) return ImportPlan("Todoist", projectName, emptyList(), emptyList(), listOf("The file is empty."))
        val head = rows.first().map { it.trim().uppercase() }
        fun col(name: String) = head.indexOf(name)
        val cType = col("TYPE"); val cContent = col("CONTENT"); val cDesc = col("DESCRIPTION")
        val cPrio = col("PRIORITY"); val cIndent = col("INDENT"); val cDate = col("DATE")
        if (cType < 0 || cContent < 0) return ImportPlan("Todoist", projectName, emptyList(), emptyList(), listOf("This doesn't look like a Todoist CSV export."))

        val warnings = mutableListOf<String>()
        val sections = mutableListOf<String>()
        var section: String? = null
        val items = mutableListOf<Pair<Int, ImportedTask>>()
        val rawPriorities = mutableListOf<Int>()
        val labelRegex = Regex("""(?<!\S)@([\p{L}\p{N}_\-]+)""")
        val ctx = ParseContext(now = now)
        for (r in rows.drop(1)) {
            fun cell(i: Int) = if (i >= 0) r.getOrNull(i)?.trim().orEmpty() else ""
            when (cell(cType).lowercase()) {
                "section" -> { section = cell(cContent); sections += section!! }
                "note" -> {
                    // Comments aren't kept as such; add them to the task above.
                    val last = items.removeLastOrNull()
                    if (last != null) items += last.first to last.second.copy(notes = listOf(last.second.notes, cell(cContent)).filter { it.isNotBlank() }.joinToString("\n\n"))
                }
                "task" -> {
                    val content = cell(cContent)
                    val labels = labelRegex.findAll(content).map { it.groupValues[1] }.toList()
                    val title = labelRegex.replace(content, "").replace(Regex("\\s+"), " ").trim()
                    val prio = cell(cPrio).toIntOrNull()?.coerceIn(1, 4) ?: 4
                    rawPriorities += prio
                    val dateText = cell(cDate)
                    val parsed = dateText.takeIf { it.isNotBlank() }?.let { QuickAddParser.parse("x $it", ctx) }
                    if (parsed != null && parsed.dueDate == null && parsed.recurrence == null) warnings += "Couldn't read the date “$dateText” on “$title”; it was added without one."
                    items += (cell(cIndent).toIntOrNull() ?: 1) to ImportedTask(
                        title = title.ifBlank { "(untitled)" }, notes = cell(cDesc), priority = prio,
                        dueDate = parsed?.dueDate, dueTime = parsed?.dueTime, recurrence = parsed?.recurrence,
                        labels = labels, section = section,
                    )
                }
            }
        }
        // Older exports number priorities like Todoist's API (4 = p1, 1 = none): then most tasks are 1.
        val reversed = rawPriorities.size >= 3 && rawPriorities.count { it == 1 } > rawPriorities.size / 2
        val plan = ImportPlan("Todoist", projectName, sections.distinct(), nest(items), warnings)
        return if (reversed) plan.flipPriorities() else plan
    }
}

/**
 * A plain-text list: one task per line, indentation (or "-", "*", "•", "[ ]") for subtasks,
 * "# Heading" or a line ending in ":" for sections, "[x]" for done. Dates and tags in each line
 * are read like quick add when [understand] is on.
 */
object PlainTextList {
    private val bullet = Regex("""^(?:[-*•+]|\d+[.)])\s+""")
    private val box = Regex("""^\[( |x|X)]\s*""")

    fun read(text: String, now: LocalDateTime, understand: Boolean = true, projectName: String? = null): ImportPlan {
        val ctx = ParseContext(now = now)
        val sections = mutableListOf<String>()
        var section: String? = null
        val items = mutableListOf<Pair<Int, ImportedTask>>()
        for (raw in text.lines()) {
            if (raw.isBlank()) continue
            val expanded = raw.replace("\t", "    ")
            val indent = expanded.length - expanded.trimStart().length
            var line = expanded.trim()
            if (line.startsWith("#")) { section = line.trimStart('#').trim(); sections += section; continue }
            if (line.endsWith(":") && indent == 0 && !bullet.containsMatchIn(line)) { section = line.dropLast(1).trim(); sections += section; continue }
            line = bullet.replace(line, "")
            var done = false
            box.find(line)?.let { done = it.groupValues[1].isNotBlank(); line = line.substring(it.range.last + 1) }
            if (line.isBlank()) continue
            val task = if (understand) {
                val p = QuickAddParser.parse(line, ctx)
                ImportedTask(
                    title = p.title.ifBlank { line }, priority = p.priority ?: 4, dueDate = p.dueDate, dueTime = p.dueTime,
                    recurrence = p.recurrence, labels = p.labels.map { it.name }, section = section, done = done,
                )
            } else {
                ImportedTask(title = line, section = section, done = done)
            }
            items += (indent / 2) to task
        }
        return ImportPlan("Text", projectName, sections.distinct(), nest(items))
    }
}
