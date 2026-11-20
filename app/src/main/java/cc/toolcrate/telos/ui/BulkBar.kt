package cc.toolcrate.telos.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.data.Undo
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.ui.theme.LocalPaper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/** Bulk actions for the selected tasks: complete, reschedule, move, label, priority, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkBar(state: AppState, vm: AppViewModel, selected: Set<String>, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    val ids = selected.toList()
    val idx = state.index

    fun moveTo(projectId: String) = scope.launch {
        val before = ids.mapNotNull { vm.repo.task(it) }
        ids.forEach { vm.repo.move(it, projectId, null, null, idx.openTasks.filter { t -> t.projectId == projectId }.maxOfOrNull { t -> t.orderKey }, null) }
        vm.notify("Moved ${ids.size} to ${idx.projects[projectId]?.name}", Undo(tasks = before))
        onDone()
    }

    BottomAppBar(containerColor = LocalPaper.current.card) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "${selected.size} selected",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    scope.launch {
                        val undos = ids.mapNotNull { vm.repo.complete(it) }
                        vm.notify("Completed ${undos.size}", Undo(undos.flatMap { it.tasks }, undos.flatMap { it.completionsToRemove }))
                        onDone()
                    }
                }) { Text("Complete") }
                Column {
                    TextButton(onClick = { menu = "date" }) { Text("Reschedule") }
                    DropdownMenu(expanded = menu == "date", onDismissRequest = { menu = null }) {
                        listOf("Today" to 0L, "Tomorrow" to 1L, "Next week" to 7L).forEach { (label, d) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { menu = null; vm.reschedule(ids, state.today.plusDays(d).toEpochDay()); onDone() })
                        }
                        DropdownMenuItem(text = { Text("Pick a date…") }, onClick = { menu = null; pickDate = true })
                        DropdownMenuItem(text = { Text("No date") }, onClick = { menu = null; vm.reschedule(ids, null); onDone() })
                    }
                }
                Column {
                    TextButton(onClick = { menu = "move" }) { Text("Move") }
                    DropdownMenu(expanded = menu == "move", onDismissRequest = { menu = null }) {
                        (listOfNotNull(idx.projects[Project.INBOX_ID]) + idx.projectTree().map { it.first }).forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { menu = null; moveTo(p.id) })
                        }
                    }
                }
                Column {
                    TextButton(onClick = { menu = "label" }) { Text("Label") }
                    DropdownMenu(expanded = menu == "label", onDismissRequest = { menu = null }) {
                        idx.labels.values.sortedBy { it.orderKey }.forEach { l ->
                            DropdownMenuItem(text = { Text("@" + l.name) }, onClick = {
                                menu = null
                                scope.launch {
                                    ids.forEach { id -> vm.repo.setLabels(id, idx.labelsByTask[id].orEmpty().map { it.id }.toSet() + l.id) }
                                    vm.notify("Labelled ${ids.size} @${l.name}")
                                    onDone()
                                }
                            })
                        }
                        if (idx.labels.isEmpty()) DropdownMenuItem(text = { Text("No labels yet") }, onClick = { menu = null }, enabled = false)
                    }
                }
                Column {
                    TextButton(onClick = { menu = "prio" }) { Text("Priority") }
                    DropdownMenu(expanded = menu == "prio", onDismissRequest = { menu = null }) {
                        (1..4).forEach { p ->
                            DropdownMenuItem(text = { Text(Format.priorityName(p)) }, onClick = {
                                menu = null
                                scope.launch {
                                    val before = ids.mapNotNull { vm.repo.task(it) }
                                    before.forEach { vm.repo.updateTask(it.copy(priority = p)) }
                                    vm.notify("Priority set", Undo(tasks = before))
                                    onDone()
                                }
                            })
                        }
                    }
                }
                TextButton(onClick = { vm.trash(ids); onDone() }) { Text("Delete") }
                TextButton(onClick = onDone) { Text("Cancel") }
            }
        }
    }
    if (pickDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { vm.reschedule(ids, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    pickDate = false
                    onDone()
                }) { Text("Set") }
            },
        ) { DatePicker(picker) }
    }
}
