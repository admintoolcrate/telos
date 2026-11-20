package cc.toolcrate.telos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.components.PageTitle
import cc.toolcrate.telos.ui.theme.LocalPaper
import kotlinx.coroutines.launch

/** A plain, tappable row of text with an optional count on the right. */
@Composable
fun LinkRow(text: String, modifier: Modifier = Modifier, indent: Int = 0, count: Int? = null, sub: String? = null, onClick: () -> Unit) {
    val paper = LocalPaper.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = text + (sub?.let { ", $it" } ?: "") + (count?.let { ", $it open" } ?: "")
            }
            .padding(start = (20 + 18 * indent).dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = paper.ink)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = paper.muted) }
        }
        count?.takeIf { it > 0 }?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = paper.muted) }
    }
}

@Composable
fun BrowseScreen(state: AppState, vm: AppViewModel, nav: Nav) {
    val idx = state.index
    var adding by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ListBody {
        item("title") { PageTitle("Browse") }
        item("search") { LinkRow("Search") { nav.go("search") } }
        item("h-projects") {
            GroupHeader("Projects") { TextButton(onClick = { adding = "project" }) { Text("New") } }
        }
        idx.projectTree().forEach { (p, depth) ->
            item("p-${p.id}") { LinkRow(p.name, indent = depth, count = idx.openCount(p.id)) { nav.openProject(p.id) } }
        }
        item("h-labels") { GroupHeader("Labels") { TextButton(onClick = { adding = "label" }) { Text("New") } } }
        idx.labels.values.sortedBy { it.orderKey }.forEach { l ->
            item("l-${l.id}") { LinkRow("@" + l.name, count = idx.withLabel(l.id).size) { nav.openLabel(l.id) } }
        }
        item("h-filters") { GroupHeader("Filters") { TextButton(onClick = { nav.go("filter/new") }) { Text("New") } } }
        state.index.snapshot.filters.filter { it.deletedAt == null }.forEach { f ->
            item("f-${f.id}") { LinkRow(f.name, sub = f.query) { nav.openFilter(f.id) } }
        }
        item("h-more") { GroupHeader("More") }
        item("completed") { LinkRow("Completed") { nav.go("completed") } }
        item("archive") { LinkRow("Archive") { nav.go("archive") } }
        item("trash") { LinkRow("Trash", sub = "Kept for 30 days") { nav.go("trash") } }
        item("data") { LinkRow("Import, export and backup") { nav.go("data") } }
        item("privacy") { LinkRow("What leaves this device") { nav.go("privacy") } }
        item("settings") { LinkRow("Settings") { nav.go("settings") } }
    }
    adding?.let { kind ->
        NameDialog(
            title = if (kind == "project") "New project" else "New label",
            onDismiss = { adding = null },
            onConfirm = { name ->
                scope.launch {
                    if (kind == "project") nav.openProject(vm.repo.addProject(name).id) else vm.repo.labelNamed(name)
                }
                adding = null
            },
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String = "", confirm: String = "Save", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

