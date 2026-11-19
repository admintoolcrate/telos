package cc.toolcrate.telos.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.ui.theme.LocalPaper

/** Bulk actions for the selected tasks. Filled out by the bulk-actions feature. */
@Composable
fun BulkBar(state: AppState, vm: AppViewModel, selected: Set<String>, onDone: () -> Unit) {
    BottomAppBar(containerColor = LocalPaper.current.card) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.size} selected", modifier = Modifier.weight(1f))
            TextButton(onClick = { selected.forEach(vm::complete); onDone() }) { Text("Complete") }
            TextButton(onClick = { vm.trash(selected); onDone() }) { Text("Delete") }
            TextButton(onClick = onDone) { Text("Cancel") }
        }
    }
}
