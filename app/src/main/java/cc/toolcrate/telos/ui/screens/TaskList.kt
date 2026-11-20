package cc.toolcrate.telos.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.domain.views.TaskItem
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.components.RowActions
import cc.toolcrate.telos.ui.components.TaskRow

/** How screens reach the rest of the app. */
class Nav(
    val openTask: (String) -> Unit,
    val openProject: (String) -> Unit,
    val openLabel: (String) -> Unit,
    val openFilter: (String) -> Unit,
    val openGoal: (String) -> Unit,
    val go: (String) -> Unit,
    val back: () -> Unit,
    /** Opens quick add, prefilled for the screen it was opened from. */
    val quickAdd: (QuickAddTarget) -> Unit,
)

data class QuickAddTarget(
    val projectId: String? = null,
    val sectionId: String? = null,
    val date: java.time.LocalDate? = null,
    val parentId: String? = null,
    val goalId: String? = null,
)

/** Selection for bulk actions, shared by every task list. */
class SelectionHandle(val selected: Set<String>, val toggle: (String) -> Unit) {
    val active: Boolean get() = selected.isNotEmpty()
}

/** One task row, wired to completion, opening, collapsing and selection. */
fun LazyListScope.taskRows(
    items: List<TaskItem>,
    state: AppState,
    vm: AppViewModel,
    nav: Nav,
    selection: SelectionHandle,
    showProject: Boolean = true,
    tree: Boolean = false,
    keyPrefix: String = "",
    extra: (TaskItem) -> List<Pair<String, () -> Unit>> = { emptyList() },
) {
    val rows = if (tree) items.flatMap { it.flatten() } else items
    items(rows, key = { keyPrefix + it.id }) { item ->
        TaskRow(
            item = item,
            today = state.today,
            showProject = showProject,
            selectionMode = selection.active,
            selected = item.id in selection.selected,
            displayTitle = vm.titleFor(item.task, state),
            actions = RowActions(
                open = { if (selection.active) selection.toggle(item.id) else nav.openTask(item.id) },
                complete = {
                    when {
                        selection.active -> selection.toggle(item.id)
                        item.task.completedAt != null -> vm.reopen(item.id)
                        else -> vm.complete(item.id)
                    }
                },
                toggleCollapse = if (tree && item.subtasksOpen > 0) ({ vm.toggleCollapsed(item.task) }) else null,
                longPress = { selection.toggle(item.id) },
                extra = listOf(
                    "Reschedule to tomorrow" to { vm.reschedule(listOf(item.id), state.today.plusDays(1).toEpochDay()); Unit },
                ) + extra(item),
            ),
        )
    }
}

fun LazyListScope.header(text: String, key: String) {
    item(key = "h-$key") { GroupHeader(text) }
}

@Composable
fun ListBody(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 96.dp), content = content)
}
