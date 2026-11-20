package cc.toolcrate.telos.ui.screens

import cc.toolcrate.telos.ui.theme.ScreenTitle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.domain.filter.EvalContext
import cc.toolcrate.telos.domain.filter.FilterQuery
import cc.toolcrate.telos.domain.filter.filterError
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.components.EmptyNote
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.theme.LocalPaper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun BackTitle(title: String, nav: Nav, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = ScreenTitle, modifier = Modifier.weight(1f).semantics { heading() })
        actions()
    }
}

@Composable
fun FilterScreen(id: String, state: AppState, vm: AppViewModel, nav: Nav, selection: SelectionHandle) {
    if (id == "new") return FilterEditScreen(null, state, vm, nav)
    val filter = state.index.snapshot.filters.firstOrNull { it.id == id && it.deletedAt == null }
        ?: return EmptyNote("This filter no longer exists.")
    var editing by remember { mutableStateOf(false) }
    if (editing) return FilterEditScreen(filter.id, state, vm, nav, onDone = { editing = false })
    val parsed = FilterQuery.parse(filter.query, state.today, state.settings.weekStart)
    ListBody {
        item("title") {
            BackTitle(filter.name, nav) { TextButton(onClick = { editing = true }) { Text("Edit") } }
            Text(filter.query, style = MaterialTheme.typography.bodySmall, color = LocalPaper.current.muted, modifier = Modifier.padding(start = 56.dp))
        }
        parsed.onSuccess { expr ->
            val ctx = EvalContext(state.index, state.today, ZoneId.systemDefault())
            val items = FilterQuery.run(expr, ctx).map { state.index.item(it) }
            taskRows(items, state, vm, nav, selection)
            if (items.isEmpty()) item("empty") { EmptyNote("Nothing matches right now.") }
        }.onFailure { e ->
            item("error") { EmptyNote(e.filterError()?.message ?: "This query can't be read.") }
        }
    }
}

private val cheatSheet = listOf(
    "today, tomorrow, overdue, no date, 7 days" to "When it's due",
    "due before: +3d, due after: next friday, due: jan 5" to "Due dates, in quick-add words",
    "p1, p2, p3, no priority" to "Priority",
    "@phone, @home*, no labels" to "Labels (* matches the start)",
    "#Work, ##Work, /Next up" to "Project, with subprojects (##), section",
    "^Run a marathon, no goal" to "Goal",
    "recurring, subtask, search: receipt" to "Other",
    "&  |  !  ( )" to "and, or, not, grouping",
)

@Composable
fun FilterEditScreen(id: String?, state: AppState, vm: AppViewModel, nav: Nav, onDone: (() -> Unit)? = null) {
    val existing = id?.let { fid -> state.index.snapshot.filters.firstOrNull { it.id == fid } }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var query by remember { mutableStateOf(existing?.query ?: "today & p1") }
    val scope = rememberCoroutineScope()
    val paper = LocalPaper.current
    val parsed = FilterQuery.parse(query, state.today, state.settings.weekStart)
    val error = parsed.exceptionOrNull()?.filterError()
    val count = parsed.getOrNull()?.let { FilterQuery.run(it, EvalContext(state.index, state.today, ZoneId.systemDefault())).size }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        BackTitle(if (existing == null) "New filter" else "Edit filter", nav)
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                query, { query = it }, label = { Text("Query") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                isError = error != null,
                supportingText = {
                    Text(
                        when {
                            error != null -> "${error.message} (at character ${error.position + 1})"
                            count == 1 -> "1 open task matches"
                            else -> "$count open tasks match"
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                },
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (existing != null) {
                    TextButton(onClick = { scope.launch { vm.repo.deleteFilter(existing.id) }; nav.back() }) { Text("Delete") }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Button(enabled = error == null && name.isNotBlank(), onClick = {
                    scope.launch {
                        val saved = vm.repo.saveFilter(existing?.id, name, query)
                        if (onDone != null) onDone() else { nav.back(); nav.openFilter(saved.id) }
                    }
                }) { Text("Save") }
            }
            GroupHeader("How to write a query")
            cheatSheet.forEach { (example, meaning) ->
                Text(example, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = paper.ink, modifier = Modifier.padding(top = 8.dp))
                Text(meaning, style = MaterialTheme.typography.bodySmall, color = paper.muted)
            }
        }
    }
}

@Composable
fun SearchScreen(state: AppState, vm: AppViewModel, nav: Nav, selection: SelectionHandle) {
    var text by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Task>>(emptyList()) }
    LaunchedEffect(text) { hits = vm.repo.search(text) }
    val idx = state.index
    val q = text.trim().lowercase()
    val projects = if (q.isEmpty()) emptyList() else idx.projectTree(includeArchived = true).map { it.first }.filter { q in it.name.lowercase() }
    val labels = if (q.isEmpty()) emptyList() else idx.labels.values.filter { q in it.name.lowercase() }
    val goals = if (q.isEmpty()) emptyList() else idx.goals.values.filter { it.trashedAt == null && (q in it.title.lowercase() || q in it.why.lowercase()) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ListBody {
        item("field") {
            BackTitle("Search", nav)
            OutlinedTextField(
                text, { text = it }, placeholder = { Text("Tasks, notes, projects, labels, goals") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
        }
        val live = hits.filter { it.trashedAt == null && it.deletedAt == null && idx.projectLive(it.projectId) }
        val open = live.filter { it.completedAt == null }
        val done = live.filter { it.completedAt != null }
        if (open.isNotEmpty()) { header("Tasks", "tasks"); taskRows(open.map { idx.item(it) }, state, vm, nav, selection, keyPrefix = "o") }
        if (done.isNotEmpty()) { header("Completed", "done"); taskRows(done.map { idx.item(it) }, state, vm, nav, selection, keyPrefix = "c") }
        if (projects.isNotEmpty()) { header("Projects", "projects"); projects.forEach { p -> item("p${p.id}") { LinkRow(p.name, sub = if (p.archivedAt != null) "Archived" else null) { nav.openProject(p.id) } } } }
        if (labels.isNotEmpty()) { header("Labels", "labels"); labels.forEach { l -> item("l${l.id}") { LinkRow("@" + l.name) { nav.openLabel(l.id) } } } }
        if (goals.isNotEmpty()) { header("Goals", "goals"); goals.forEach { g -> item("g${g.id}") { LinkRow(g.title) { nav.openGoal(g.id) } } } }
        if (q.isNotEmpty() && live.isEmpty() && projects.isEmpty() && labels.isEmpty() && goals.isEmpty()) item("none") { EmptyNote("Nothing found for “${text.trim()}”.") }
    }
}

@Composable
fun CompletedScreen(state: AppState, vm: AppViewModel, nav: Nav) {
    val entries = state.index.completed().filter { e -> e.task?.let { it.deletedAt == null } == true }
    val zone = ZoneId.systemDefault()
    val byDay = entries.groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.completion.completedAt), zone) }
    ListBody {
        item("title") { BackTitle("Completed", nav) }
        byDay.forEach { (day, list) ->
            header(Format.day(day, state.today), day.toString())
            list.forEach { e ->
                item(e.completion.id) {
                    val t = e.task!!
                    LinkRow(
                        "✓ " + vm.titleFor(t, state),
                        sub = listOfNotNull(e.project?.takeIf { !it.isInbox }?.name, if (t.completedAt == null && t.recurrence != null) "repeats" else null).joinToString(" · ").ifEmpty { null },
                    ) { nav.openTask(t.id) }
                }
            }
        }
        if (entries.isEmpty()) item("empty") { EmptyNote("Completed tasks will be listed here.") }
    }
}

@Composable
fun ArchiveScreen(state: AppState, vm: AppViewModel, nav: Nav) {
    val scope = rememberCoroutineScope()
    val projects = state.index.archivedProjects()
    val goals = state.index.goals.values.filter { it.archivedAt != null && it.trashedAt == null }
    ListBody {
        item("title") { BackTitle("Archive", nav) }
        if (projects.isNotEmpty()) header("Projects", "p")
        projects.forEach { p ->
            item(p.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { LinkRow(p.name) { nav.openProject(p.id) } }
                    TextButton(onClick = { scope.launch { vm.repo.unarchiveProject(p.id) } }) { Text("Unarchive") }
                }
            }
        }
        if (goals.isNotEmpty()) header("Goals", "g")
        goals.forEach { g -> item(g.id) { LinkRow(g.title, sub = g.status.name.lowercase().replaceFirstChar { it.uppercase() }) { nav.openGoal(g.id) } } }
        if (projects.isEmpty() && goals.isEmpty()) item("empty") { EmptyNote("Nothing archived.") }
    }
}

@Composable
fun TrashScreen(state: AppState, vm: AppViewModel, nav: Nav) {
    val scope = rememberCoroutineScope()
    val tasks = state.index.trashedTasks()
    val projects = state.index.trashedProjects()
    var confirmEmpty by remember { mutableStateOf(false) }
    val paper = LocalPaper.current
    ListBody {
        item("title") {
            BackTitle("Trash", nav) {
                if (tasks.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Empty") }
            }
            Text("Items are removed for good 30 days after you delete them.", style = MaterialTheme.typography.bodySmall, color = paper.muted, modifier = Modifier.padding(start = 56.dp, bottom = 8.dp))
        }
        projects.forEach { p ->
            item("p" + p.id) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Text("#" + p.name, modifier = Modifier.weight(1f).padding(start = 20.dp))
                    TextButton(onClick = { scope.launch { vm.repo.restoreProject(p.id) } }) { Text("Restore") }
                }
            }
        }
        tasks.forEach { t ->
            item(t.id) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Column(Modifier.weight(1f).padding(start = 20.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(vm.titleFor(t, state), style = MaterialTheme.typography.bodyLarge)
                        val left = Index.daysLeftInTrash(t, System.currentTimeMillis())
                        Text("Deleted · ${left} days left", style = MaterialTheme.typography.bodySmall, color = paper.muted)
                    }
                    TextButton(onClick = { scope.launch { vm.repo.restore(listOf(t.id)) } }) { Text("Restore") }
                    TextButton(onClick = { scope.launch { vm.repo.purge(listOf(t.id)) } }) { Text("Delete") }
                }
            }
        }
        if (tasks.isEmpty() && projects.isEmpty()) item("empty") { EmptyNote("The trash is empty.") }
    }
    if (confirmEmpty) ConfirmDialog("Empty the trash?", "These tasks will be removed for good. This can't be undone.", "Empty", onDismiss = { confirmEmpty = false }) {
        scope.launch { vm.repo.purge(tasks.map { it.id }) }
    }
}
