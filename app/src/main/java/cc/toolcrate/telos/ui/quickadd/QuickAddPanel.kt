package cc.toolcrate.telos.ui.quickadd

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.quickadd.Span
import cc.toolcrate.telos.domain.recurrence.describe
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.theme.LocalPaper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The quick-add panel: one text field, parsed as you type, with each recognised part shown as a
 * chip you can remove (it goes back into the title) and pickers that override the text.
 * Enter saves. Used in the in-app sheet and the capture dialog (widget, tile, share sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddPanel(
    vm: QuickAddViewModel,
    projects: List<Project>,
    today: LocalDate,
    onSaved: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val preview = vm.preview(state)
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showProjects by remember { mutableStateOf(false) }
    var showPriority by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun save() = scope.launch { vm.save()?.let(onSaved) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = state.text,
            onValueChange = vm::setText,
            placeholder = { Text("e.g. Call dentist tomorrow 3pm #Health p2") },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "New task" },
            visualTransformation = Highlight(preview.parsed.spans, paper.accentSoft),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            maxLines = 4,
        )

        // What will be saved, spoken politely to TalkBack as it changes.
        val summary = buildList {
            add(preview.parsed.title.ifBlank { "(no title yet)" })
            preview.date?.let { add(Format.day(it, today) + (preview.time?.let { t -> " " + Format.time(t.hour * 60 + t.minute) } ?: "")) }
            preview.parsed.recurrence?.let { add(it.describe()) }
            preview.projectName?.let { add("#$it" + if (preview.projectId == null) " (new)" else "") }
            preview.parsed.labels.forEach { add("@" + it.name) }
            if (preview.priority < 4) add("P${preview.priority}")
        }.joinToString(" · ")
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall, color = paper.muted,
            modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Parsed tokens: tap ✕ to keep that text in the title instead.
            preview.parsed.spans.forEach { span ->
                InputChip(
                    selected = true,
                    onClick = { vm.dismiss(span.kind, span.text) },
                    label = { Text(span.text.trim()) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Keep \"${span.text.trim()}\" as text") },
                )
            }
            AssistChip(
                onClick = { showDate = true },
                label = { Text(preview.date?.let { Format.day(it, today) } ?: "Date") },
            )
            AssistChip(
                onClick = { showTime = true },
                label = { Text(preview.time?.let { Format.time(it.hour * 60 + it.minute) } ?: "Time") },
            )
            Column {
                AssistChip(onClick = { showProjects = true }, label = { Text(preview.projectName ?: "Inbox") })
                DropdownMenu(expanded = showProjects, onDismissRequest = { showProjects = false }) {
                    projects.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.pickProject(p.id); showProjects = false })
                    }
                }
            }
            Column {
                AssistChip(onClick = { showPriority = true }, label = { Text(if (preview.priority < 4) "P${preview.priority}" else "Priority") })
                DropdownMenu(expanded = showPriority, onDismissRequest = { showPriority = false }) {
                    (1..4).forEach { p ->
                        DropdownMenuItem(text = { Text(Format.priorityName(p)) }, onClick = { vm.pickPriority(p); showPriority = false })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = { save() }, enabled = preview.parsed.title.isNotBlank()) { Text("Add task") }
        }
    }

    if (showDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (preview.date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { vm.pickDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDate = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { vm.pickDate(null); showDate = false }) { Text("No date") } },
        ) { DatePicker(picker) }
    }
    if (showTime) {
        TimeDialog(
            initial = preview.time ?: LocalTime.of(9, 0),
            onPick = { vm.pickTime(it); showTime = false },
            onDismiss = { showTime = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onPick: (LocalTime?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("No time") } },
        text = { TimePicker(state) },
    )
}

/** Tints the recognised parts of the text so you can see what was understood. */
private class Highlight(private val spans: List<Span>, private val color: androidx.compose.ui.graphics.Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text)
            spans.filter { it.end <= text.length }.forEach { addStyle(SpanStyle(background = color), it.start, it.end) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is Highlight && other.spans == spans
    override fun hashCode() = spans.hashCode()
}

