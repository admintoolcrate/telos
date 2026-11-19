package cc.toolcrate.telos.backup

import androidx.room.withTransaction
import cc.toolcrate.telos.data.DeviceIdentity
import cc.toolcrate.telos.data.Repository
import cc.toolcrate.telos.data.TelosDatabase
import cc.toolcrate.telos.data.TimeSource
import cc.toolcrate.telos.domain.io.Codec
import cc.toolcrate.telos.domain.io.CsvExport
import cc.toolcrate.telos.domain.io.ImportPlan
import cc.toolcrate.telos.domain.io.ImportedTask
import cc.toolcrate.telos.domain.io.Merge
import cc.toolcrate.telos.domain.io.MergeResult
import cc.toolcrate.telos.domain.io.PlainTextList
import cc.toolcrate.telos.domain.io.TodoistCsv
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.TaskDraft
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import cc.toolcrate.telos.domain.time.WallTime
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** What a picked file turned out to be. */
sealed interface Incoming {
    data class TelosExport(val snapshot: Snapshot) : Incoming
    data object EncryptedBackup : Incoming
    data class Plan(val plan: ImportPlan) : Incoming
}

/** Export, import and merge: everything that moves data in or out as a whole. */
@Singleton
class DataTransfer @Inject constructor(
    private val db: TelosDatabase,
    private val repo: Repository,
    private val time: TimeSource,
    private val device: DeviceIdentity,
) {
    /** Every record, tombstones included. */
    suspend fun snapshotAll(): Snapshot = Snapshot(
        projects = db.projects().everything(), sections = db.sections().everything(), tasks = db.tasks().everything(),
        labels = db.labels().everything(), taskLabels = db.labels().everyLink(), reminders = db.reminders().everything(),
        completions = db.completions().everything(), filters = db.filters().everything(), goals = db.goals().everything(),
        reviews = db.reviews().everything(),
    )

    suspend fun exportJson(): String = Codec.encode(snapshotAll(), time.now())

    suspend fun exportCsv(): String = CsvExport.write(snapshotAll(), time.zone())

    /** Merges [incoming] into the database: newer records win, nothing newer here is lost. */
    suspend fun merge(incoming: Snapshot): MergeResult = db.withTransaction {
        val local = snapshotAll()
        val result = Merge.merge(local, incoming)
        val s = result.snapshot
        fun <T> changed(merged: List<T>, before: List<T>): List<T> { val old = before.toSet(); return merged.filter { it !in old } }
        db.projects().upsertAll(changed(s.projects, local.projects))
        db.sections().upsertAll(changed(s.sections, local.sections))
        db.tasks().upsertAll(changed(s.tasks, local.tasks))
        db.labels().upsertAll(changed(s.labels, local.labels))
        db.labels().upsertLinks(changed(s.taskLabels, local.taskLabels))
        db.reminders().upsertAll(changed(s.reminders, local.reminders))
        db.completions().upsertAll(changed(s.completions, local.completions))
        db.filters().upsertAll(changed(s.filters, local.filters))
        db.goals().upsertAll(changed(s.goals, local.goals))
        db.reviews().upsertAll(changed(s.reviews, local.reviews))
        result
    }

    /** Works out what [text] is (named [fileName]) without changing anything. */
    fun identify(text: String, fileName: String?): Incoming {
        if (cc.toolcrate.telos.domain.backup.Envelope.looksLike(text)) return Incoming.EncryptedBackup
        Codec.decode(text)?.let { return Incoming.TelosExport(it.snapshot) }
        val name = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        val now = LocalDateTime.now(time.clock())
        return if (TodoistCsv.looksLike(text)) Incoming.Plan(TodoistCsv.read(text, now, name)) else Incoming.Plan(PlainTextList.read(text, now, projectName = name))
    }

    /**
     * Adds an import to [targetProjectId], or to a new project named [newProjectName].
     * Returns the project the tasks went to.
     */
    suspend fun commit(plan: ImportPlan, targetProjectId: String?, newProjectName: String?): Project = db.withTransaction {
        val project = targetProjectId?.let { repo.project(it) } ?: repo.addProject(newProjectName?.ifBlank { null } ?: plan.suggestedProject ?: "Imported")
        val sections = (plan.sections + plan.tasks.flatMap { it.flatten() }.mapNotNull { it.section }).distinct()
            .associateWith { name -> db.sections().forProject(project.id).firstOrNull { it.name.equals(name, true) } ?: repo.addSection(project.id, name) }

        suspend fun add(t: ImportedTask, parentId: String?) {
            val task = repo.addTask(
                TaskDraft(
                    title = t.title, notes = t.notes, projectId = project.id, sectionId = t.section?.let { sections[it]?.id },
                    parentId = parentId, priority = t.priority, dueDate = t.dueDate?.toEpochDay(), dueMinute = t.dueTime?.let(WallTime::minuteOf),
                    recurrence = t.recurrence?.let(RecurrenceCodec::encode), labels = t.labels,
                )
            )
            if (t.done) repo.complete(task.id)
            t.children.forEach { add(it, task.id) }
        }
        plan.tasks.forEach { add(it, null) }
        project
    }

}
