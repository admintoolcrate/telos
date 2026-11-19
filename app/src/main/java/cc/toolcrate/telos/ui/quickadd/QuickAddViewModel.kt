package cc.toolcrate.telos.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.toolcrate.telos.data.GoalRepository
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.Repository
import cc.toolcrate.telos.data.TimeSource
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.ReminderSpec
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.model.TaskDraft
import cc.toolcrate.telos.domain.quickadd.Named
import cc.toolcrate.telos.domain.quickadd.NamedSection
import cc.toolcrate.telos.domain.quickadd.ParseContext
import cc.toolcrate.telos.domain.quickadd.ParsedTask
import cc.toolcrate.telos.domain.quickadd.QuickAddParser
import cc.toolcrate.telos.domain.quickadd.Ref
import cc.toolcrate.telos.domain.quickadd.TokenKind
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import cc.toolcrate.telos.domain.time.WallTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject

/** Something picked by hand in the sheet; wins over what the text says. */
sealed interface Pick<out T> {
    data object None : Pick<Nothing>
    data object Cleared : Pick<Nothing>
    data class Value<T>(val value: T) : Pick<T>
}

data class QuickAddState(
    val text: String = "",
    val ignore: Set<Pair<TokenKind, String>> = emptySet(),
    val date: Pick<LocalDate> = Pick.None,
    val time: Pick<LocalTime> = Pick.None,
    val projectId: Pick<String> = Pick.None,
    val priority: Pick<Int> = Pick.None,
    /** Where the task goes when nothing else says: the screen it was added from. */
    val defaultProjectId: String = Project.INBOX_ID,
    val defaultSectionId: String? = null,
    val defaultDate: LocalDate? = null,
    val parentId: String? = null,
    val goalId: String? = null,
)

/** The parsed text with the hand-picked values applied: what Save will create. */
data class Preview(
    val parsed: ParsedTask,
    val date: LocalDate?,
    val time: LocalTime?,
    val projectName: String?,
    val projectId: String?,
    val priority: Int,
)

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val repo: Repository,
    private val goals: GoalRepository,
    private val prefs: Prefs,
    private val time: TimeSource,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickAddState())
    val state: StateFlow<QuickAddState> = _state.asStateFlow()

    private val snapshot: StateFlow<Snapshot> = repo.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, Snapshot())

    fun start(projectId: String? = null, sectionId: String? = null, date: LocalDate? = null, parentId: String? = null,
              goalId: String? = null, text: String = "") {
        _state.value = QuickAddState(
            text = text, defaultProjectId = projectId ?: Project.INBOX_ID, defaultSectionId = sectionId,
            defaultDate = date, parentId = parentId, goalId = goalId,
        )
    }

    fun setText(text: String) = _state.update { it.copy(text = text) }
    fun dismiss(kind: TokenKind, text: String) = _state.update { it.copy(ignore = it.ignore + (kind to text)) }
    fun pickDate(d: LocalDate?) = _state.update { it.copy(date = d?.let { v -> Pick.Value(v) } ?: Pick.Cleared) }
    fun pickTime(t: LocalTime?) = _state.update { it.copy(time = t?.let { v -> Pick.Value(v) } ?: Pick.Cleared) }
    fun pickProject(id: String) = _state.update { it.copy(projectId = Pick.Value(id)) }
    fun pickPriority(p: Int) = _state.update { it.copy(priority = Pick.Value(p)) }

    fun context(s: QuickAddState = _state.value, snap: Snapshot = snapshot.value): ParseContext {
        val live = snap.projects.filter { it.deletedAt == null && it.trashedAt == null && it.archivedAt == null }
        return ParseContext(
            now = LocalDateTime.now(time.clock()),
            projects = live.map { Named(it.id, it.name) },
            sections = snap.sections.filter { it.deletedAt == null }.map { NamedSection(it.id, it.name, it.projectId) },
            labels = snap.labels.filter { it.deletedAt == null }.map { it.name },
            goals = snap.goals.filter { it.deletedAt == null }.map { Named(it.id, it.title) },
            dayFirst = dayFirst(),
            ignore = s.ignore,
            defaultProjectId = s.defaultProjectId,
        )
    }

    fun preview(s: QuickAddState, snap: Snapshot = snapshot.value): Preview {
        val parsed = QuickAddParser.parse(s.text, context(s, snap))
        val date = when (val d = s.date) {
            is Pick.Value -> d.value
            Pick.Cleared -> null
            Pick.None -> parsed.dueDate ?: s.defaultDate
        }
        val t = when (val v = s.time) {
            is Pick.Value -> v.value
            Pick.Cleared -> null
            Pick.None -> parsed.dueTime
        }
        val projects = snap.projects.associateBy { it.id }
        val (pid, pname) = when (val p = s.projectId) {
            is Pick.Value -> p.value to projects[p.value]?.name
            else -> when (val r = parsed.project) {
                is Ref.Existing -> r.id to r.name
                is Ref.New -> null to r.name
                null -> s.defaultProjectId to projects[s.defaultProjectId]?.name
            }
        }
        val priority = (s.priority as? Pick.Value)?.value ?: parsed.priority ?: 4
        return Preview(parsed, date, t, pname, pid, priority)
    }

    /** Creates the task, and any new project, section, labels or goal it names. Null if the title is empty. */
    suspend fun save(): Task? {
        val s = _state.value
        val p = preview(s)
        val title = p.parsed.title.ifBlank { return null }
        val projectId = p.projectId ?: repo.addProject((p.parsed.project as Ref.New).name).id
        val sectionId = when (val sec = p.parsed.section) {
            is Ref.Existing -> sec.id
            is Ref.New -> repo.addSection(projectId, sec.name).id
            null -> s.defaultSectionId.takeIf { projectId == s.defaultProjectId }
        }
        val goalId = when (val g = p.parsed.goal) {
            is Ref.Existing -> g.id
            is Ref.New -> goals.add(g.name).id
            null -> s.goalId
        }
        val reminders = buildList<ReminderSpec> {
            addAll(p.parsed.reminders)
            if (isEmpty() && p.time != null) prefs.current().defaultReminder?.let { add(ReminderSpec.Before(it)) }
        }
        val task = repo.addTask(
            TaskDraft(
                title = title, projectId = projectId, sectionId = sectionId, parentId = s.parentId, goalId = goalId,
                priority = p.priority, dueDate = p.date?.toEpochDay(), dueMinute = p.time?.let(WallTime::minuteOf),
                recurrence = p.parsed.recurrence?.let { r -> RecurrenceCodec.encode(if (p.date != null) r.copy(anchor = p.date) else r) },
                labels = p.parsed.labels.map { it.name }, reminders = reminders,
            )
        )
        // Ready for the next one, keeping where it goes.
        _state.update { QuickAddState(defaultProjectId = it.defaultProjectId, defaultSectionId = it.defaultSectionId, defaultDate = it.defaultDate, parentId = it.parentId, goalId = it.goalId) }
        return task
    }

    private fun dayFirst(): Boolean {
        val pattern = (DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern() ?: return true
        return pattern.indexOf('d') < pattern.indexOf('M')
    }
}
