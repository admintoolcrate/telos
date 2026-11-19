package cc.toolcrate.telos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.domain.recurrence.RecurrenceCodec
import cc.toolcrate.telos.domain.recurrence.describe
import cc.toolcrate.telos.domain.views.TaskItem
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.theme.LocalPaper
import java.time.LocalDate

/** What a row can do; each one is also offered to TalkBack as a custom action. */
class RowActions(
    val open: () -> Unit,
    val complete: () -> Unit,
    val toggleCollapse: (() -> Unit)? = null,
    val longPress: (() -> Unit)? = null,
    val extra: List<Pair<String, () -> Unit>> = emptyList(),
)

@Composable
fun priorityColor(p: Int): Color {
    val paper = LocalPaper.current
    return when (p) {
        1 -> paper.p1
        2 -> paper.p2
        3 -> paper.p3
        else -> paper.muted
    }
}

/** The completion circle. Priority shows as colour *and* ring weight (and as text in the meta line). */
@Composable
fun CompleteCircle(priority: Int, done: Boolean, modifier: Modifier = Modifier) {
    val color = priorityColor(priority)
    val weight = when (priority) {
        1 -> 2.5.dp
        2 -> 2.dp
        3 -> 1.6.dp
        else -> 1.2.dp
    }
    Box(
        modifier
            .size(22.dp)
            .border(BorderStroke(weight, color), CircleShape)
            .background(if (done) color else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Default.Check, contentDescription = null, tint = LocalPaper.current.page, modifier = Modifier.size(14.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    item: TaskItem,
    today: LocalDate,
    actions: RowActions,
    modifier: Modifier = Modifier,
    showProject: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    /** Shown in place of the title (private titles in shared surfaces). */
    displayTitle: String = item.task.title,
    trailing: (@Composable () -> Unit)? = null,
) {
    val paper = LocalPaper.current
    val t = item.task
    val done = t.completedAt != null
    val due = Format.due(t.dueDate, t.dueMinute, today)
    val rule = t.recurrence?.let(RecurrenceCodec::decode)
    val meta = buildList {
        if (t.priority in 1..3) add("P${t.priority}")
        due?.let { add(it) }
        rule?.let { add("↻") }
        if (item.hasReminder) add("⏰")
        if (item.subtasksTotal > 0) add("${item.subtasksTotal - item.subtasksOpen}/${item.subtasksTotal}")
        item.project?.takeIf { showProject && !it.isInbox }?.let { add("#" + it.name) }
        item.labels.forEach { add("@" + it.name) }
    }
    val spoken = buildString {
        append(displayTitle)
        if (done) append(", completed")
        if (t.priority in 1..3) append(", ${Format.priorityName(t.priority)}")
        due?.let { append(", due $it") }
        rule?.let { append(", repeats ${it.describe()}") }
        if (item.hasReminder) append(", has a reminder")
        if (item.subtasksTotal > 0) append(", ${item.subtasksOpen} of ${item.subtasksTotal} subtasks open")
        item.project?.takeIf { showProject && !it.isInbox }?.let { append(", in ${it.name}") }
        if (item.labels.isNotEmpty()) append(", labels ${item.labels.joinToString { it.name }}")
    }
    val customActions = buildList {
        add(CustomAccessibilityAction(if (done) "Reopen" else "Complete") { actions.complete(); true })
        actions.toggleCollapse?.let { add(CustomAccessibilityAction(if (t.collapsed) "Show subtasks" else "Hide subtasks") { it(); true }) }
        actions.longPress?.let { add(CustomAccessibilityAction("Select") { it(); true }) }
        actions.extra.forEach { (label, f) -> add(CustomAccessibilityAction(label) { f(); true }) }
    }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) paper.accentSoft else Color.Transparent)
                .combinedClickable(onClick = actions.open, onLongClick = actions.longPress)
                .semantics(mergeDescendants = true) {}
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                    if (selectionMode) {
                        this.selected = selected
                        stateDescription = if (selected) "Selected" else "Not selected"
                    }
                    onClick(label = if (selectionMode) "Toggle selection" else "Open") { actions.open(); true }
                    this.customActions = customActions
                }
                .heightIn(min = 48.dp)
                .padding(start = (8 + 20 * item.depth).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (actions.toggleCollapse != null && item.subtasksOpen > 0) {
                IconButton(onClick = actions.toggleCollapse, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (t.collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                        contentDescription = null, tint = paper.muted,
                    )
                }
            } else {
                Spacer(Modifier.width(36.dp))
            }
            Box(
                Modifier
                    .size(40.dp)
                    .combinedClickable(onClick = actions.complete),
                contentAlignment = Alignment.Center,
            ) {
                if (selectionMode) {
                    Box(
                        Modifier.size(22.dp).border(1.5.dp, paper.accent, MaterialTheme.shapes.extraSmall)
                            .background(if (selected) paper.accent else Color.Transparent, MaterialTheme.shapes.extraSmall),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = paper.page, modifier = Modifier.size(16.dp))
                    }
                } else {
                    CompleteCircle(t.priority, done)
                }
            }
            Column(Modifier.weight(1f).padding(start = 6.dp, top = 8.dp)) {
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) paper.muted else paper.ink,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (t.notes.isNotBlank()) {
                    Text(
                        t.notes.lineSequence().first(),
                        style = MaterialTheme.typography.bodySmall, color = paper.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
                        meta.forEach { m ->
                            Text(
                                m,
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    m == "P1" -> paper.p1
                                    m == "P2" -> paper.p2
                                    m == "P3" -> paper.p3
                                    else -> paper.muted
                                },
                            )
                        }
                    }
                }
            }
            trailing?.invoke()
        }
        HorizontalDivider(color = paper.rule, thickness = 0.5.dp, modifier = Modifier.padding(start = (84 + 20 * item.depth).dp))
    }
}
