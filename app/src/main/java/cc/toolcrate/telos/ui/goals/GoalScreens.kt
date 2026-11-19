package cc.toolcrate.telos.ui.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.domain.goals.GoalSummary
import cc.toolcrate.telos.domain.goals.Goals
import cc.toolcrate.telos.domain.goals.Quarter
import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.GoalStatus
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.components.CompleteCircle
import cc.toolcrate.telos.ui.components.EmptyNote
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.components.PageTitle
import cc.toolcrate.telos.ui.screens.BackTitle
import cc.toolcrate.telos.ui.screens.ConfirmDialog
import cc.toolcrate.telos.ui.screens.LinkRow
import cc.toolcrate.telos.ui.screens.ListBody
import cc.toolcrate.telos.ui.screens.Nav
import cc.toolcrate.telos.ui.screens.QuickAddTarget
import cc.toolcrate.telos.ui.screens.SelectionHandle
import cc.toolcrate.telos.ui.screens.header
import cc.toolcrate.telos.ui.screens.taskRows
import cc.toolcrate.telos.ui.theme.Garamond
import cc.toolcrate.telos.ui.theme.LocalPaper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private fun goals(state: AppState) = Goals(state.index, System.currentTimeMillis(), ZoneId.systemDefault())

/** A thin, quiet ring. No percentages shouted; the numbers are there for TalkBack and on the detail page. */
@Composable
fun ProgressRing(done: Int, total: Int, size: Dp = 36.dp) {
    val paper = LocalPaper.current
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Canvas(Modifier.size(size).semantics { contentDescription = if (total == 0) "No tasks yet" else "$done of $total tasks done" }) {
        val stroke = size.toPx() * 0.09f
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(paper.rule, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (fraction > 0f) drawArc(paper.accent, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun Why(text: String, maxLines: Int = Int.MAX_VALUE) {
    if (text.isBlank()) return
    Text(
        text, fontFamily = Garamond, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.titleMedium,
        color = LocalPaper.current.muted, maxLines = maxLines,
    )
}

@Composable
private fun GoalCard(s: GoalSummary, state: AppState, vm: AppViewModel, nav: Nav) {
    val paper = LocalPaper.current
    val title = vm.goalTitle(s.goal, state)
    Card(
        colors = CardDefaults.cardColors(containerColor = paper.card),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { nav.openGoal(s.goal.id) }
            .semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = paper.ink)
                Why(s.goal.why, maxLines = 2)
                s.nextStep?.let { Text("Next: " + vm.titleFor(it.task, state), style = MaterialTheme.typography.bodyMedium, color = paper.ink, modifier = Modifier.padding(top = 8.dp)) }
                s.goal.targetDate?.let { Text("By " + Format.day(LocalDate.ofEpochDay(it), state.today), style = MaterialTheme.typography.bodySmall, color = paper.muted) }
            }
            ProgressRing(s.done, s.total)
        }
    }
}

@Composable
fun GoalsScreen(state: AppState, vm: AppViewModel, nav: Nav) {
    val g = goals(state)
    val visible = g.visible().map(g::summary)
    val quarter = Quarter.of(state.today)
    val reviewDue = state.settings.lastReviewQuarter != quarter.toString() && visible.isNotEmpty() && state.today.plusDays(21) > quarter.end
    ListBody {
        item("title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PageTitle("Goals", subtitle = "The why behind the work") }
                TextButton(onClick = { nav.go("goaledit/new") }, modifier = Modifier.padding(end = 8.dp)) { Text("New goal") }
            }
        }
        item("links") {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { nav.go("next") }, label = { Text("Next steps") })
                AssistChip(onClick = { nav.go("review") }, label = { Text(if (reviewDue) "Quarterly review · ${quarter.label}" else "Quarterly review") })
            }
        }
        val byStatus = visible.groupBy { it.goal.status }
        for ((status, label) in listOf(GoalStatus.ACTIVE to "Active", GoalStatus.PAUSED to "Paused", GoalStatus.ACHIEVED to "Achieved")) {
            val list = byStatus[status].orEmpty()
            if (list.isEmpty()) continue
            if (byStatus.size > 1 || status != GoalStatus.ACTIVE) header(label, "s$status")
            list.forEach { s -> item(s.goal.id) { GoalCard(s, state, vm, nav) } }
        }
        if (visible.isEmpty()) item("empty") { EmptyNote("A goal is a reason for the work. Name one, say why it matters, and link the projects that serve it.") }
    }
}

@Composable
fun NextStepsScreen(state: AppState, vm: AppViewModel, nav: Nav) {
    val active = goals(state).active()
    val paper = LocalPaper.current
    ListBody {
        item("title") { BackTitle("Next steps", nav) }
        item("sub") { Text("One concrete action for each active goal.", color = paper.muted, modifier = Modifier.padding(horizontal = 20.dp)) }
        active.forEach { s ->
            item(s.goal.id) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(vm.goalTitle(s.goal, state), style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { nav.openGoal(s.goal.id) })
                    val step = s.nextStep
                    if (step == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("No next step yet.", color = paper.muted, modifier = Modifier.weight(1f))
                            TextButton(onClick = { nav.quickAdd(QuickAddTarget(goalId = s.goal.id)) }) { Text("Add one") }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) {},
                        ) {
                            Box(
                                Modifier.size(44.dp).clickable(onClickLabel = "Complete") { vm.complete(step.id) }
                                    .semantics { contentDescription = "Complete ${vm.titleFor(step.task, state)}" },
                                contentAlignment = Alignment.Center,
                            ) { CompleteCircle(step.task.priority, false) }
                            Column(Modifier.weight(1f).clickable { nav.openTask(step.id) }) {
                                Text(vm.titleFor(step.task, state), style = MaterialTheme.typography.bodyLarge)
                                Format.due(step.task.dueDate, step.task.dueMinute, state.today)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = paper.muted) }
                            }
                        }
                    }
                }
            }
        }
        if (active.isEmpty()) item("empty") { EmptyNote("No active goals.") }
    }
}

@Composable
fun GoalDetailScreen(id: String, state: AppState, vm: AppViewModel, nav: Nav, selection: SelectionHandle) {
    val goal = state.index.goals[id] ?: return EmptyNote("This goal no longer exists.")
    val s = goals(state).summary(goal)
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    ListBody {
        item("title") {
            BackTitle("", nav) { TextButton(onClick = { nav.go("goaledit/${goal.id}") }) { Text("Edit") } }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(vm.goalTitle(goal, state), style = MaterialTheme.typography.headlineMedium)
                Why(goal.why)
                goal.targetDate?.let { Text("By " + Format.longDay(LocalDate.ofEpochDay(it)), style = MaterialTheme.typography.bodySmall, color = paper.muted, modifier = Modifier.padding(top = 4.dp)) }
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(s.done, s.total, 56.dp)
                    Spacer(Modifier.size(16.dp))
                    Text(if (s.total == 0) "No tasks yet" else "${s.done} of ${s.total} tasks done", color = paper.muted)
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalStatus.entries.forEach { st ->
                        FilterChip(selected = goal.status == st, onClick = { scope.launch { vm.goals.setStatus(goal.id, st) } },
                            label = { Text(st.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }
        item("next") {
            GroupHeader("Next step")
            val step = s.nextStep
            if (step == null) {
                TextButton(onClick = { nav.quickAdd(QuickAddTarget(goalId = goal.id)) }, modifier = Modifier.padding(start = 8.dp)) { Text("Add a first step") }
            }
        }
        s.nextStep?.let { taskRows(listOf(it), state, vm, nav, selection, keyPrefix = "next-") }
        item("projects") {
            GroupHeader("Projects") { TextButton(onClick = { dialog = "link" }) { Text("Link") } }
            if (s.projects.isEmpty()) Text("No projects linked yet.", color = paper.muted, modifier = Modifier.padding(horizontal = 20.dp))
            s.projects.forEach { p -> LinkRow(p.name, count = state.index.openCount(p.id)) { nav.openProject(p.id) } }
        }
        header("Open tasks", "open")
        taskRows(s.open, state, vm, nav, selection, tree = true, keyPrefix = "o-", extra = { item ->
            listOf((if (item.task.pinnedNext) "Unpin as next step" else "Pin as next step") to {
                scope.launch {
                    state.index.liveTasks.filter { it.pinnedNext && state.index.goalOf(it) == goal.id && it.id != item.id }
                        .forEach { vm.repo.updateTask(it.copy(pinnedNext = false)) }
                    vm.repo.updateTask(item.task.copy(pinnedNext = !item.task.pinnedNext))
                }
                Unit
            })
        })
        item("actions") {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.quickAdd(QuickAddTarget(goalId = goal.id)) }) { Text("Add task") }
                TextButton(onClick = { scope.launch { vm.goals.archive(goal.id, goal.archivedAt == null) }; if (goal.archivedAt == null) nav.back() }) {
                    Text(if (goal.archivedAt == null) "Archive" else "Unarchive")
                }
                TextButton(onClick = { dialog = "delete" }) { Text("Delete") }
            }
        }
    }
    when (dialog) {
        "link" -> {
            val candidates = state.index.projectTree().map { it.first }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Link a project") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (candidates.isEmpty()) Text("No projects yet.")
                        candidates.forEach { p ->
                            LinkRow(p.name + if (p.goalId == goal.id) "  ✓" else "") {
                                scope.launch { vm.repo.saveProject(p.copy(goalId = if (p.goalId == goal.id) null else goal.id)) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
            )
        }
        "delete" -> ConfirmDialog("Delete goal?", "Its projects and tasks stay; they just won't be linked to it. You can restore the goal from the trash.", "Delete",
            onDismiss = { dialog = null }) { scope.launch { vm.goals.trash(goal.id) }; nav.back() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(id: String?, state: AppState, vm: AppViewModel, nav: Nav) {
    val existing = id?.takeIf { it != "new" }?.let { state.index.goals[it] }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var why by remember { mutableStateOf(existing?.why.orEmpty()) }
    var target by remember { mutableStateOf(existing?.targetDate) }
    var cover by remember { mutableStateOf(existing?.coverTitle.orEmpty()) }
    var pick by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackTitle(if (existing == null) "New goal" else "Edit goal", nav)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Goal") }, placeholder = { Text("Run a marathon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(why, { why = it }, label = { Text("Why it matters") }, placeholder = { Text("To prove to myself I can keep a long promise") },
                minLines = 3, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(target?.let { "Target: " + Format.longDay(LocalDate.ofEpochDay(it)) } ?: "No target date", modifier = Modifier.weight(1f))
                TextButton(onClick = { pick = true }) { Text("Set") }
                if (target != null) TextButton(onClick = { target = null }) { Text("Clear") }
            }
            OutlinedTextField(cover, { cover = it }, label = { Text("Cover title") },
                supportingText = { Text("Shown instead of the goal's name in widgets and notifications.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = title.isNotBlank(), onClick = {
                scope.launch {
                    if (existing == null) {
                        val g = vm.goals.add(title, why, target)
                        if (cover.isNotBlank()) vm.goals.save(g.copy(coverTitle = cover.trim()))
                        nav.back(); nav.openGoal(g.id)
                    } else {
                        vm.goals.save(existing.copy(title = title.trim(), why = why.trim(), targetDate = target, coverTitle = cover.trim().ifBlank { null }))
                        nav.back()
                    }
                }
            }, modifier = Modifier.align(Alignment.End)) { Text("Save") }
        }
    }
    if (pick) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (target?.let(LocalDate::ofEpochDay) ?: state.today.plusMonths(3)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pick = false }, confirmButton = {
            TextButton(onClick = { target = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }; pick = false }) { Text("Set") }
        }) { DatePicker(picker) }
    }
}

@Composable
fun ReviewScreen(state: AppState, vm: AppViewModel, nav: Nav) {
    val review = goals(state).review(state.today)
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    val notes = state.index.snapshot.reviews.filter { it.deletedAt == null }.sortedByDescending { it.createdAt }
    ListBody {
        item("title") {
            BackTitle("${review.quarter.label} review", nav)
            Text(
                "A look back, not a report card. Keep what still matters, rest what doesn't, and change anything that has drifted.",
                color = paper.muted, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        header("Achieved this quarter", "achieved")
        if (review.achieved.isEmpty()) item("a-none") { Text("None yet this quarter, and that's fine.", color = paper.muted, modifier = Modifier.padding(horizontal = 20.dp)) }
        review.achieved.forEach { s -> item("a" + s.goal.id) { LinkRow(vm.goalTitle(s.goal, state), sub = s.goal.why.ifBlank { null }) { nav.openGoal(s.goal.id) } } }

        header("Quiet for 30 days or more", "stalled")
        if (review.stalled.isEmpty()) item("s-none") { Text("Every active goal has seen some movement.", color = paper.muted, modifier = Modifier.padding(horizontal = 20.dp)) }
        review.stalled.forEach { s ->
            item("s" + s.goal.id) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(vm.goalTitle(s.goal, state), style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { nav.openGoal(s.goal.id) })
                    Why(s.goal.why, 2)
                    Text("Is this still the right goal?", style = MaterialTheme.typography.bodySmall, color = paper.muted, modifier = Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { scope.launch { vm.goals.save(s.goal) }; vm.notify("Kept") }) { Text("Keep") }
                        TextButton(onClick = { scope.launch { vm.goals.setStatus(s.goal.id, GoalStatus.PAUSED) } }) { Text("Pause") }
                        TextButton(onClick = { nav.go("goaledit/${s.goal.id}") }) { Text("Adjust why") }
                        TextButton(onClick = { scope.launch { vm.goals.setStatus(s.goal.id, GoalStatus.ACHIEVED) } }) { Text("Achieved") }
                    }
                }
            }
        }
        if (review.moving.isNotEmpty()) {
            header("Moving", "moving")
            review.moving.forEach { s -> item("m" + s.goal.id) { LinkRow(vm.goalTitle(s.goal, state), sub = "${s.done} of ${s.total} done") { nav.openGoal(s.goal.id) } } }
        }
        if (review.paused.isNotEmpty()) {
            header("Resting", "paused")
            review.paused.forEach { s -> item("p" + s.goal.id) { LinkRow(vm.goalTitle(s.goal, state), sub = "Paused") { nav.openGoal(s.goal.id) } } }
        }
        item("note") {
            GroupHeader("A note to yourself")
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(note, { note = it }, placeholder = { Text("What would you like to change next quarter?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    scope.launch {
                        if (note.isNotBlank()) vm.goals.addReview(review.quarter.toString(), note)
                        vm.prefs.update { copy(lastReviewQuarter = review.quarter.toString()) }
                        note = ""
                        vm.notify("Review saved")
                    }
                }, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) { Text("Finish review") }
            }
        }
        if (notes.isNotEmpty()) {
            header("Earlier notes", "notes")
            notes.forEach { n ->
                item(n.id) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text(Quarter.parse(n.quarter)?.label ?: n.quarter, style = MaterialTheme.typography.labelMedium, color = paper.muted)
                        Text(n.note, fontFamily = Garamond, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

