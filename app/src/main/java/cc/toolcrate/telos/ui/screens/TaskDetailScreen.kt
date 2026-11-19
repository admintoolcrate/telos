package cc.toolcrate.telos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.quickadd.ParseContext
import cc.toolcrate.telos.domain.quickadd.QuickAddParser
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import cc.toolcrate.telos.domain.recurrence.describe
import cc.toolcrate.telos.domain.time.WallTime
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.components.EmptyNote
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.quickadd.TimeDialog
import cc.toolcrate.telos.ui.theme.LocalPaper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(taskId: String, state: AppState, vm: AppViewModel, nav: Nav, extraSections: @Composable (Task) -> Unit = {}) {
    val idx = state.index
    val task = idx.tasksById[taskId]?.takeIf { it.deletedAt == null } ?: run { EmptyNote("This task no longer exists."); return }
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    var title by remember(taskId) { mutableStateOf(task.title) }
    var notes by remember(taskId) { mutableStateOf(task.notes) }
    var cover by remember(taskId) { mutableStateOf(task.coverTitle.orEmpty()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showProject by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var repeatText by remember(taskId) { mutableStateOf("") }
    var newSubtask by remember { mutableStateOf("") }
    val current by rememberUpdatedState(task)

    fun save(change: (Task) -> Task) = scope.launch { vm.repo.updateTask(change(current)) }

    // Text fields save when you leave the screen (and on Done), not on every keystroke.
    fun saveText() {
        val t = current
        if (t.title != title.trim() && title.isNotBlank() || t.notes != notes || (t.coverTitle ?: "") != cover.trim()) {
            vm.viewModelScopeLaunch { vm.repo.updateTask(t.copy(title = title.trim().ifBlank { t.title }, notes = notes, coverTitle = cover.trim().ifBlank { null })) }
        }
    }
    DisposableEffect(taskId) { onDispose { saveText() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onClick = { saveText(); nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                if (task.trashedAt == null) {
                    IconButton(onClick = { vm.trash(listOf(task.id)); nav.back() }) { Icon(Icons.Default.Delete, contentDescription = "Move to trash") }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = paper.page),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            task.parentId?.let { idx.tasksById[it] }?.let { parent ->
                TextButton(onClick = { nav.openTask(parent.id) }) { Text("Subtask of ${parent.title}") }
            }
            TextField(
                value = title, onValueChange = { title = it },
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Title" },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveText() }),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2,
            )

            if (task.completedAt != null) {
                TextButton(onClick = { vm.reopen(task.id) }) { Text("Completed · Reopen") }
            }

            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { showDate = true }, label = { Text(task.dueDate?.let { Format.day(LocalDate.ofEpochDay(it), state.today) } ?: "Add date") })
                AssistChip(onClick = { showTime = true }, label = { Text(task.dueMinute?.let { Format.time(it) } ?: "Add time") })
                Column {
                    AssistChip(onClick = { showProject = true }, label = {
                        Text((idx.projects[task.projectId]?.name ?: "Inbox") + (task.sectionId?.let { idx.sections[it] }?.let { " / ${it.name}" } ?: ""))
                    })
                    DropdownMenu(expanded = showProject, onDismissRequest = { showProject = false }) {
                        val inbox = idx.projects[cc.toolcrate.telos.domain.model.Project.INBOX_ID]
                        (listOfNotNull(inbox) + idx.projectTree().map { it.first }).forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = {
                                showProject = false
                                scope.launch { vm.repo.move(task.id, p.id, null, null, null, null) }
                            })
                            idx.snapshot.sections.filter { it.projectId == p.id && it.deletedAt == null }.sortedBy { it.orderKey }.forEach { s ->
                                DropdownMenuItem(text = { Text("    ${s.name}") }, onClick = {
                                    showProject = false
                                    scope.launch { vm.repo.move(task.id, p.id, s.id, null, null, null) }
                                })
                            }
                        }
                    }
                }
                AssistChip(onClick = { showGoal = true }, label = { Text(task.goalId?.let { idx.goals[it]?.title }?.let { "Goal: $it" } ?: "Link a goal") })
            }

            GroupHeader("Priority")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { p ->
                    FilterChip(
                        selected = task.priority == p,
                        onClick = { save { it.copy(priority = p) } },
                        label = { Text(if (p == 4) "None" else "P$p") },
                        modifier = Modifier.semantics { contentDescription = Format.priorityName(p) },
                    )
                }
            }

            GroupHeader("Repeat")
            val rule = task.recurrence?.let(RecurrenceCodec::decode)
            Text(rule?.describe() ?: "Doesn't repeat", style = MaterialTheme.typography.bodyMedium, color = paper.muted)
            val parsedRule = repeatText.takeIf { it.isNotBlank() }?.let {
                QuickAddParser.parse(it, ParseContext(now = LocalDateTime.of(state.today, LocalTime.now()), weekStart = state.settings.weekStart)).recurrence
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = repeatText, onValueChange = { repeatText = it },
                    placeholder = { Text("every weekday, 3 days after completion…") },
                    supportingText = { Text(parsedRule?.describe() ?: if (repeatText.isBlank()) "" else "Not understood yet") },
                    modifier = Modifier.weight(1f), singleLine = true,
                )
                TextButton(enabled = parsedRule != null, onClick = {
                    val r = parsedRule ?: return@TextButton
                    val start = task.dueDate?.let(LocalDate::ofEpochDay) ?: state.today
                    val first = r.copy(anchor = start).copy(interval = 1).firstOnOrAfter(start) ?: start
                    save { it.copy(recurrence = RecurrenceCodec.encode(r.copy(anchor = first)), dueDate = first.toEpochDay()) }
                    repeatText = ""
                }) { Text("Set") }
            }
            if (rule != null) TextButton(onClick = { save { it.copy(recurrence = null) } }) { Text("Stop repeating") }

            GroupHeader("Labels")
            val mine = idx.labelsByTask[task.id].orEmpty().map { it.id }.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                idx.labels.values.sortedBy { it.orderKey }.forEach { l ->
                    FilterChip(
                        selected = l.id in mine,
                        onClick = { scope.launch { vm.repo.setLabels(task.id, if (l.id in mine) mine - l.id else mine + l.id) } },
                        label = { Text("@" + l.name) },
                    )
                }
                var naming by remember { mutableStateOf(false) }
                AssistChip(onClick = { naming = true }, label = { Text("New label") })
                if (naming) NameDialog("New label", onDismiss = { naming = false }) { name ->
                    scope.launch { vm.repo.setLabelNames(task.id, listOf(name)) }; naming = false
                }
            }

            extraSections(task)

            GroupHeader("Subtasks")
            val subs = idx.liveTasks.filter { it.parentId == task.id }.sortedWith(compareBy({ it.completedAt != null }, { it.orderKey }))
            subs.forEach { s ->
                LinkRow((if (s.completedAt != null) "✓ " else "○ ") + s.title, sub = Format.due(s.dueDate, s.dueMinute, state.today)) { nav.openTask(s.id) }
            }
            OutlinedTextField(
                value = newSubtask, onValueChange = { newSubtask = it },
                placeholder = { Text("Add a subtask") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val text = newSubtask.trim()
                    if (text.isNotEmpty()) {
                        scope.launch {
                            vm.repo.addTask(cc.toolcrate.telos.domain.model.TaskDraft(title = text, projectId = task.projectId, sectionId = task.sectionId, parentId = task.id))
                        }
                        newSubtask = ""
                    }
                }),
            )

            GroupHeader("Privacy")
            OutlinedTextField(
                value = cover, onValueChange = { cover = it },
                label = { Text("Cover title") },
                supportingText = { Text("Shown instead of the title in widgets and notifications.") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            HorizontalDivider(Modifier.padding(vertical = 24.dp), color = paper.rule)
            Text(
                "Created " + Format.day(LocalDate.ofInstant(Instant.ofEpochMilli(task.createdAt), java.time.ZoneId.systemDefault()), state.today),
                style = MaterialTheme.typography.bodySmall, color = paper.muted, modifier = Modifier.padding(bottom = 48.dp),
            )
        }
    }

    if (showDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (task.dueDate?.let(LocalDate::ofEpochDay) ?: state.today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        save { it.copy(dueDate = d.toEpochDay()) }
                    }
                    showDate = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { save { it.copy(dueDate = null, dueMinute = null) }; showDate = false }) { Text("No date") } },
        ) { DatePicker(picker) }
    }
    if (showTime) {
        TimeDialog(
            initial = task.dueMinute?.let(WallTime::timeOf) ?: LocalTime.of(9, 0),
            onPick = { t ->
                save { it.copy(dueMinute = t?.let(WallTime::minuteOf), dueDate = it.dueDate ?: if (t != null) state.today.toEpochDay() else null) }
                showTime = false
            },
            onDismiss = { showTime = false },
        )
    }
    if (showGoal) {
        GoalPickerDialog(state, task.goalId, onDismiss = { showGoal = false }) { g -> save { it.copy(goalId = g) }; showGoal = false }
    }
    LaunchedEffect(task.title) { if (title.isBlank()) title = task.title }
}

@Composable
fun LabelScreen(labelId: String, state: AppState, vm: AppViewModel, nav: Nav, selection: SelectionHandle) {
    val label = state.index.labels[labelId] ?: run { EmptyNote("This label no longer exists."); return }
    val items = state.index.withLabel(labelId)
    var dialog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ListBody {
        item("title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("@" + label.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { dialog = "rename" }) { Text("Rename") }
                TextButton(onClick = { dialog = "delete" }) { Text("Delete") }
            }
        }
        taskRows(items, state, vm, nav, selection)
        if (items.isEmpty()) item("empty") { EmptyNote("No open tasks with this label.") }
    }
    when (dialog) {
        "rename" -> NameDialog("Rename label", initial = label.name, onDismiss = { dialog = null }) { n ->
            scope.launch { vm.repo.saveLabel(label.copy(name = n)) }; dialog = null
        }
        "delete" -> ConfirmDialog("Delete label?", "Tasks keep everything else; only the label is removed.", "Delete", onDismiss = { dialog = null }) {
            scope.launch { vm.repo.deleteLabel(label.id) }; nav.back()
        }
    }
}
