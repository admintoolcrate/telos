package cc.toolcrate.telos.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Section
import cc.toolcrate.telos.domain.model.ViewMode
import cc.toolcrate.telos.domain.views.TaskItem
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.components.EmptyNote
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.components.RowActions
import cc.toolcrate.telos.ui.components.TaskRow
import cc.toolcrate.telos.ui.theme.LocalPaper
import cc.toolcrate.telos.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch

/** A row of the flattened project list: a section header or a task. */
private sealed interface PRow {
    val key: String

    data class Header(val section: Section?) : PRow {
        override val key = "h-" + (section?.id ?: "none")
    }

    data class TaskR(val item: TaskItem, val section: Section?) : PRow {
        override val key = item.id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(projectId: String, state: AppState, vm: AppViewModel, nav: Nav, selection: SelectionHandle) {
    val idx = state.index
    val project = idx.projects[projectId] ?: run { EmptyNote("This project no longer exists."); return }
    val groups = idx.project(projectId)
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var sectionDialog by remember { mutableStateOf<Section?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(project.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Project options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val board = project.viewMode == ViewMode.BOARD
                    DropdownMenuItem(text = { Text(if (board) "Show as list" else "Show as board") }, onClick = {
                        menu = false
                        scope.launch { vm.repo.saveProject(project.copy(viewMode = if (board) ViewMode.LIST else ViewMode.BOARD)) }
                    })
                    DropdownMenuItem(text = { Text("Add section") }, onClick = { menu = false; dialog = "section" })
                    if (!project.isInbox) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; dialog = "rename" })
                        DropdownMenuItem(text = { Text("Link to a goal") }, onClick = { menu = false; dialog = "goal" })
                        DropdownMenuItem(text = { Text("Archive") }, onClick = {
                            menu = false
                            scope.launch { vm.repo.archiveProject(project.id, true) }
                            vm.notify("Project archived")
                            nav.back()
                        })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; dialog = "delete" })
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalPaper.current.page),
        )
        project.goalId?.let { idx.goals[it] }?.let { goal ->
            TextButton(onClick = { nav.openGoal(goal.id) }, modifier = Modifier.padding(start = 8.dp)) { Text("Towards: ${goal.title}") }
        }
        if (project.viewMode == ViewMode.BOARD) {
            Board(project, groups, state, vm, nav)
        } else {
            ProjectList(project, groups, state, vm, nav, selection, onEditSection = { sectionDialog = it })
        }
    }

    when (dialog) {
        "section" -> NameDialog("New section", onDismiss = { dialog = null }) { name ->
            scope.launch { vm.repo.addSection(project.id, name) }; dialog = null
        }
        "rename" -> NameDialog("Rename project", initial = project.name, onDismiss = { dialog = null }) { name ->
            scope.launch { vm.repo.saveProject(project.copy(name = name)) }; dialog = null
        }
        "delete" -> ConfirmDialog(
            "Delete project?", "“${project.name}” and its tasks move to the trash, where you can restore them for 30 days.", "Delete",
            onDismiss = { dialog = null },
        ) { scope.launch { vm.repo.trashProject(project.id) }; nav.back() }
        "goal" -> GoalPickerDialog(state, current = project.goalId, onDismiss = { dialog = null }) { goalId ->
            scope.launch { vm.repo.saveProject(project.copy(goalId = goalId)) }; dialog = null
        }
    }
    sectionDialog?.let { s ->
        SectionDialog(s, onDismiss = { sectionDialog = null }, onRename = { name ->
            scope.launch { vm.repo.saveSection(s.copy(name = name)) }; sectionDialog = null
        }, onDelete = {
            scope.launch { vm.repo.deleteSection(s.id) }; sectionDialog = null
        })
    }
}

@Composable
private fun ProjectList(
    project: Project,
    groups: List<cc.toolcrate.telos.domain.views.ProjectGroup>,
    state: AppState,
    vm: AppViewModel,
    nav: Nav,
    selection: SelectionHandle,
    onEditSection: (Section) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun build(rootsOnly: Boolean): List<PRow> = groups.flatMap { g ->
        val header = if (g.section != null || groups.size > 1) listOf(PRow.Header(g.section)) else emptyList()
        header + (if (rootsOnly) g.items else g.items.flatMap { it.flatten() }).map { PRow.TaskR(it, g.section) }
    }

    // While dragging, the working order lives here; otherwise it follows the data.
    val working = remember { mutableStateListOf<PRow>() }
    if (draggingKey == null) {
        working.clear()
        working.addAll(build(rootsOnly = false))
    }

    /** Siblings at the top level of the same section, in order. */
    fun rootsIn(rows: List<PRow>, section: Section?) =
        rows.filterIsInstance<PRow.TaskR>().filter { it.section?.id == section?.id && it.item.depth == 0 }

    fun moveBy(item: TaskItem, section: Section?, delta: Int) {
        val roots = rootsIn(build(true), section)
        val i = roots.indexOfFirst { it.key == item.id }
        val j = i + delta
        if (i < 0 || j !in roots.indices) return
        val others = roots.filter { it.key != item.id }
        val before = others.getOrNull(j - 1)?.item?.task?.orderKey
        val after = others.getOrNull(j)?.item?.task?.orderKey
        scope.launch { vm.repo.move(item.id, project.id, section?.id, null, before, after) }
    }

    fun commitDrag() {
        val key = draggingKey ?: return
        val i = working.indexOfFirst { it.key == key }
        val section = working.subList(0, maxOf(i, 0)).filterIsInstance<PRow.Header>().lastOrNull()?.section
        val before = working.subList(0, i).lastOrNull()?.let { (it as? PRow.TaskR)?.item?.task?.orderKey }
        val after = working.subList(i + 1, working.size).firstOrNull()?.let { (it as? PRow.TaskR)?.item?.task?.orderKey }
        scope.launch { vm.repo.move(key, project.id, section?.id, null, before, after) }
    }

    if (working.none { it is PRow.TaskR } && groups.all { it.section == null }) {
        EmptyNote("No tasks here yet. Add one with the + button.")
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        items(working.toList(), key = { it.key }) { row ->
            when (row) {
                is PRow.Header -> GroupHeader(row.section?.name ?: "No section") {
                    TextButton(onClick = { nav.quickAdd(QuickAddTarget(projectId = project.id, sectionId = row.section?.id)) }) { Text("Add") }
                    row.section?.let { s ->
                        IconButton(onClick = { onEditSection(s) }) { Icon(Icons.Default.MoreVert, contentDescription = "Section options for ${s.name}") }
                    }
                }
                is PRow.TaskR -> {
                    val item = row.item
                    val dragging = draggingKey == item.id
                    TaskRow(
                        item = item,
                        today = state.today,
                        showProject = false,
                        selectionMode = selection.active,
                        selected = item.id in selection.selected,
                        displayTitle = vm.titleFor(item.task, state),
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) dragOffset else 0f
                                if (dragging && !reduceMotion) { scaleX = 1.02f; scaleY = 1.02f }
                            },
                        actions = RowActions(
                            open = { if (selection.active) selection.toggle(item.id) else nav.openTask(item.id) },
                            complete = { if (selection.active) selection.toggle(item.id) else vm.complete(item.id) },
                            toggleCollapse = if (item.subtasksOpen > 0) ({ vm.toggleCollapsed(item.task) }) else null,
                            longPress = { selection.toggle(item.id) },
                            extra = if (item.depth == 0) listOf("Move up" to { moveBy(item, row.section, -1) }, "Move down" to { moveBy(item, row.section, 1) }) else emptyList(),
                        ),
                        trailing = if (item.depth == 0 && !selection.active) ({
                            // Drag handle: hold and drag to reorder, including into another section.
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .pointerInput(item.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                working.clear(); working.addAll(build(rootsOnly = true))
                                                draggingKey = item.id; dragOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val infos = listState.layoutInfo.visibleItemsInfo
                                                val me = infos.firstOrNull { it.key == item.id } ?: return@detectDragGesturesAfterLongPress
                                                val center = me.offset + dragOffset + me.size / 2
                                                val target = infos.firstOrNull { it.key != item.id && center > it.offset && center < it.offset + it.size }
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val from = working.indexOfFirst { it.key == item.id }
                                                val to = working.indexOfFirst { it.key == target.key }
                                                if (from < 0 || to < 0) return@detectDragGesturesAfterLongPress
                                                // Never above the first section header.
                                                if (to == 0 && working[0] is PRow.Header) return@detectDragGesturesAfterLongPress
                                                working.add(to, working.removeAt(from))
                                                dragOffset += me.offset - target.offset
                                            },
                                            onDragEnd = { commitDrag(); draggingKey = null; dragOffset = 0f },
                                            onDragCancel = { draggingKey = null; dragOffset = 0f },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = null, tint = LocalPaper.current.rule)
                            }
                        }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun Board(
    project: Project,
    groups: List<cc.toolcrate.telos.domain.views.ProjectGroup>,
    state: AppState,
    vm: AppViewModel,
    nav: Nav,
) {
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val sections = groups.mapNotNull { it.section }
    LazyRow(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(groups, key = { it.section?.id ?: "none" }) { g ->
            Column(Modifier.width(290.dp).fillMaxHeight()) {
                GroupHeader(g.section?.name ?: "No section") {
                    Text(g.items.size.toString(), color = paper.muted, style = MaterialTheme.typography.labelMedium)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(g.items, key = { it.id }) { item ->
                        var cardMenu by remember { mutableStateOf(false) }
                        val targets = (listOf<Section?>(null) + sections).filter { it?.id != g.section?.id }
                        Card(colors = CardDefaults.cardColors(containerColor = paper.card)) {
                            Box {
                                TaskRow(
                                    item = item, today = state.today, showProject = false,
                                    displayTitle = vm.titleFor(item.task, state),
                                    actions = RowActions(
                                        open = { nav.openTask(item.id) },
                                        complete = { vm.complete(item.id) },
                                        longPress = { cardMenu = true },
                                        extra = targets.map { t ->
                                            "Move to ${t?.name ?: "No section"}" to {
                                                scope.launch { vm.repo.move(item.id, project.id, t?.id, null, lastKey(groups, t), null) }
                                                Unit
                                            }
                                        },
                                    ),
                                )
                                DropdownMenu(expanded = cardMenu, onDismissRequest = { cardMenu = false }) {
                                    targets.forEach { t ->
                                        DropdownMenuItem(text = { Text("Move to ${t?.name ?: "No section"}") }, onClick = {
                                            cardMenu = false
                                            scope.launch { vm.repo.move(item.id, project.id, t?.id, null, lastKey(groups, t), null) }
                                        })
                                    }
                                }
                            }
                        }
                    }
                    item("add") {
                        TextButton(onClick = { nav.quickAdd(QuickAddTarget(projectId = project.id, sectionId = g.section?.id)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Add task")
                        }
                    }
                }
            }
        }
        item("new-section") {
            var naming by remember { mutableStateOf(false) }
            TextButton(onClick = { naming = true }, modifier = Modifier.padding(top = 18.dp)) { Text("Add section") }
            if (naming) NameDialog("New section", onDismiss = { naming = false }) { name ->
                scope.launch { vm.repo.addSection(project.id, name) }; naming = false
            }
        }
    }
}

private fun lastKey(groups: List<cc.toolcrate.telos.domain.views.ProjectGroup>, section: Section?): String? =
    groups.firstOrNull { it.section?.id == section?.id }?.items?.lastOrNull()?.task?.orderKey

@Composable
fun SectionDialog(section: Section, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    if (renaming) {
        NameDialog("Rename section", initial = section.name, onDismiss = onDismiss, onConfirm = onRename)
    } else {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(section.name) },
            text = { Text("Deleting a section keeps its tasks in the project.") },
            confirmButton = { TextButton(onClick = { renaming = true }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = onDelete) { Text("Delete section") } },
        )
    }
}

@Composable
fun GoalPickerDialog(state: AppState, current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val goals = state.index.goals.values.filter { it.trashedAt == null && it.archivedAt == null }.sortedBy { it.orderKey }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Towards which goal?") },
        text = {
            Column {
                if (goals.isEmpty()) Text("No goals yet. Add one from the Goals tab.")
                goals.forEach { g ->
                    LinkRow(g.title + if (g.id == current) "  ✓" else "") { onPick(g.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text("No goal") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
