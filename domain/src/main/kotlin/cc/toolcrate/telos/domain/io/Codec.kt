package cc.toolcrate.telos.domain.io

import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import cc.toolcrate.telos.domain.recurrence.describe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The full JSON export: every record, tombstones included, so it can be merged back later. */
@Serializable
data class Export(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val snapshot: Snapshot,
) {
    companion object {
        const val FORMAT = "telos"
        const val VERSION = 1
    }
}

object Codec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
    }

    fun encode(snapshot: Snapshot, exportedAt: Long): String = json.encodeToString(Export.serializer(), Export(exportedAt = exportedAt, snapshot = snapshot))

    /** Null when [text] isn't a Telos export. */
    fun decode(text: String): Export? = runCatching { json.decodeFromString(Export.serializer(), text) }
        .getOrNull()?.takeIf { it.format == Export.FORMAT }

    fun looksLikeExport(text: String): Boolean = text.trimStart().startsWith("{") && text.contains("\"format\"") && text.contains("\"telos\"")
}

/** A spreadsheet of open and completed tasks (no tombstones), for reading elsewhere. */
object CsvExport {
    val header = listOf("id", "title", "notes", "project", "section", "parent_id", "priority", "due_date", "due_time", "repeat", "labels", "goal", "completed_at", "created_at")

    fun write(s: Snapshot, zone: ZoneId): String {
        val projects = s.projects.associateBy { it.id }
        val sections = s.sections.associateBy { it.id }
        val goals = s.goals.associateBy { it.id }
        val labels = s.labels.associateBy { it.id }
        val taskLabels = s.taskLabels.filter { it.deletedAt == null }.groupBy { it.taskId }
        fun ts(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().withNano(0).toString() }.orEmpty()
        val rows = s.tasks.filter { it.deletedAt == null && it.trashedAt == null }.sortedBy { it.createdAt }.map { t ->
            listOf(
                t.id, t.title, t.notes, projects[t.projectId]?.name.orEmpty(), t.sectionId?.let { sections[it]?.name }.orEmpty(),
                t.parentId.orEmpty(), "p${t.priority}", t.dueDate?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(),
                t.dueMinute?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty(),
                t.recurrence?.let(RecurrenceCodec::decode)?.describe().orEmpty(),
                taskLabels[t.id].orEmpty().mapNotNull { labels[it.labelId]?.name }.joinToString(" "),
                t.goalId?.let { goals[it]?.title }.orEmpty(), ts(t.completedAt), ts(t.createdAt),
            )
        }
        return (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",", transform = Csv::escape) } + "\r\n"
    }
}

/** RFC 4180 CSV: quoted fields, doubled quotes, line breaks inside quotes. */
object Csv {
    fun escape(v: String): String = if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = if (text.startsWith('﻿')) 1 else 0
        var fieldStarted = false
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (text.getOrNull(i + 1) == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> { quoted = true; fieldStarted = true }
                ',' -> { row += field.toString(); field.clear(); fieldStarted = true }
                '\r' -> {}
                '\n' -> {
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf(); fieldStarted = false
                }
                else -> { field.append(c); fieldStarted = true }
            }
            i++
        }
        if (fieldStarted || field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}
