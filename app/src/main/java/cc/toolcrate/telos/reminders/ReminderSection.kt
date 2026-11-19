package cc.toolcrate.telos.reminders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.domain.model.ReminderKind
import cc.toolcrate.telos.domain.model.ReminderSpec
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.time.WallTime
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.quickadd.TimeDialog
import cc.toolcrate.telos.ui.theme.LocalPaper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val beforeChoices = listOf(0 to "At the due time", 5 to "5 minutes before", 10 to "10 minutes before", 15 to "15 minutes before",
    30 to "30 minutes before", 60 to "1 hour before", 120 to "2 hours before", 1440 to "1 day before")

fun describeBefore(min: Int): String = beforeChoices.firstOrNull { it.first == min }?.second ?: when {
    min % 1440 == 0 -> "${min / 1440} days before"
    min % 60 == 0 -> "${min / 60} hours before"
    else -> "$min minutes before"
}

/** The task screen's reminders: list, add (before the due time or at a set time), remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSection(task: Task, state: AppState, vm: AppViewModel) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val reminders = state.index.snapshot.reminders.filter { it.taskId == task.id && it.deletedAt == null }
    var menu by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val scheduler = remember { ReminderAccess.scheduler(context) }

    fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    GroupHeader("Reminders")
    Column {
        reminders.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val text = when (r.kind) {
                    ReminderKind.BEFORE -> describeBefore(r.minutesBefore ?: 0) + if (task.dueMinute == null) " (9:00 on the day)" else ""
                    ReminderKind.AT -> Format.due(r.atDate, r.atMinute, state.today) ?: ""
                }
                Text(text, modifier = Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { scope.launch { vm.repo.deleteReminder(r.id) } }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove reminder: $text")
                }
            }
        }
        if (task.dueDate == null && reminders.isEmpty()) {
            Text("Add a date to remind relative to it, or pick an exact time.", style = MaterialTheme.typography.bodySmall, color = paper.muted)
        }
        Row {
            Column {
                TextButton(onClick = { menu = true }, enabled = task.dueDate != null) { Text("Before due") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    beforeChoices.forEach { (min, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menu = false
                            ensurePermission()
                            scope.launch { vm.repo.addReminder(task.id, ReminderSpec.Before(min)) }
                        })
                    }
                }
            }
            TextButton(onClick = { pickDate = true }) { Text("At a time…") }
        }
        if (scheduler?.exactAllowed() == false) {
            Text(
                "Reminders may arrive a few minutes late until exact alarms are allowed.",
                style = MaterialTheme.typography.bodySmall, color = paper.muted,
            )
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 31) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }) { Text("Allow exact alarms") }
        }
    }

    if (pickDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (task.dueDate?.let(LocalDate::ofEpochDay) ?: state.today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Next") }
            },
        ) { DatePicker(picker) }
    }
    pickedDate?.let { date ->
        TimeDialog(
            initial = task.dueMinute?.let(WallTime::timeOf) ?: LocalTime.of(9, 0),
            onPick = { t ->
                if (t != null) {
                    ensurePermission()
                    scope.launch { vm.repo.addReminder(task.id, ReminderSpec.At(date.toEpochDay(), WallTime.minuteOf(t))) }
                }
                pickedDate = null
            },
            onDismiss = { pickedDate = null },
        )
    }
}

/** Reaches the singleton scheduler from Compose without threading it through every screen. */
object ReminderAccess {
    fun scheduler(context: android.content.Context): ReminderScheduler? = runCatching {
        dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, Entry::class.java).scheduler()
    }.getOrNull()

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface Entry {
        fun scheduler(): ReminderScheduler
    }
}
