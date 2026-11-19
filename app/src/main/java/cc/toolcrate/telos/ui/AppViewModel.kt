package cc.toolcrate.telos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.Repository
import cc.toolcrate.telos.data.Settings
import cc.toolcrate.telos.data.TimeSource
import cc.toolcrate.telos.data.Undo
import cc.toolcrate.telos.domain.views.Index
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Everything the screens draw from: one index per database change, today's date, settings. */
data class AppState(val index: Index, val today: LocalDate, val settings: Settings)

/** A message for the snackbar, with an optional undo. */
data class Notice(val text: String, val undo: Undo? = null)

@HiltViewModel
class AppViewModel @Inject constructor(
    val repo: Repository,
    val prefs: Prefs,
    private val time: TimeSource,
) : ViewModel() {

    /** Today's date, re-checked every 30 seconds so the views roll over at midnight. */
    private val today = flow {
        while (true) {
            emit(time.today())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<AppState?> = combine(repo.snapshot, today, prefs.settings) { snap, day, settings ->
        AppState(Index(snap), day, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val notices: SharedFlow<Notice> = _notices

    init {
        viewModelScope.launch { repo.ensureInbox() }
    }

    fun notify(text: String, undo: Undo? = null) {
        _notices.tryEmit(Notice(text, undo))
    }

    fun complete(id: String) = viewModelScope.launch {
        val before = repo.task(id)
        val undo = repo.complete(id) ?: return@launch
        val after = repo.task(id)
        val recurred = after?.completedAt == null
        notify(if (recurred) "Done · next ${Format.due(after?.dueDate, null, time.today()) ?: "time"}" else "Completed", undo)
        if (before == null) return@launch
    }

    fun reopen(id: String) = viewModelScope.launch { repo.reopen(id) }

    fun undo(undo: Undo) = viewModelScope.launch { repo.undo(undo) }

    fun trash(ids: Collection<String>) = viewModelScope.launch {
        val undo = repo.trash(ids)
        notify(if (ids.size == 1) "Moved to trash" else "${ids.size} tasks moved to trash", undo)
    }

    fun reschedule(ids: Collection<String>, epochDay: Long?, keepTime: Boolean = true) = viewModelScope.launch {
        val befores = ids.mapNotNull { repo.task(it) }
        befores.forEach { t ->
            repo.updateTask(t.copy(dueDate = epochDay, dueMinute = if (keepTime && epochDay != null) t.dueMinute else null))
        }
        notify(if (ids.size == 1) "Rescheduled" else "${ids.size} tasks rescheduled", Undo(tasks = befores))
    }

    /** The title to show: a private task's cover title while "private in app" is on. */
    fun titleFor(task: cc.toolcrate.telos.domain.model.Task, state: AppState): String =
        task.coverTitle?.takeIf { state.settings.privateInApp && it.isNotBlank() } ?: task.title

    fun toggleCollapsed(task: cc.toolcrate.telos.domain.model.Task) = viewModelScope.launch {
        repo.updateTask(task.copy(collapsed = !task.collapsed))
    }

    /** Runs [block] in the view model's scope, so it finishes even as a screen leaves. */
    fun viewModelScopeLaunch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun setTheme(change: Settings.() -> Settings) = viewModelScope.launch { prefs.update(change) }
}
