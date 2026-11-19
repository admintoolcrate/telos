package cc.toolcrate.telos.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.components.EmptyNote
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.components.PageTitle
import cc.toolcrate.telos.ui.theme.LocalPaper
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun TodayScreen(state: AppState, vm: AppViewModel, nav: Nav, selection: SelectionHandle) {
    val view = state.index.today(state.today)
    ListBody {
        item("title") { PageTitle("Today", subtitle = Format.longDay(state.today)) }
        if (view.earlier.isNotEmpty()) {
            // Overdue work, shown plainly: no red, no count, no reproach.
            item("earlier") {
                GroupHeader("Earlier") {
                    TextButton(onClick = { vm.reschedule(view.earlier.map { it.id }, state.today.toEpochDay()) }) { Text("Move to today") }
                }
            }
            taskRows(view.earlier, state, vm, nav, selection, keyPrefix = "e")
        }
        if (view.earlier.isNotEmpty() && view.today.isNotEmpty()) header("Today", "today")
        taskRows(view.today, state, vm, nav, selection, keyPrefix = "t")
        if (view.earlier.isEmpty() && view.today.isEmpty()) {
            item("empty") { EmptyNote("Nothing scheduled for today.") }
        }
    }
}

@Composable
fun InboxScreen(state: AppState, vm: AppViewModel, nav: Nav, selection: SelectionHandle) {
    val items = state.index.inbox()
    ListBody {
        item("title") { PageTitle("Inbox") }
        taskRows(items, state, vm, nav, selection, showProject = false, tree = true)
        if (items.isEmpty()) item("empty") { EmptyNote("Your inbox is clear.") }
    }
}

@Composable
fun UpcomingScreen(state: AppState, vm: AppViewModel, nav: Nav, selection: SelectionHandle) {
    val paper = LocalPaper.current
    var selectedEpoch by rememberSaveable { mutableStateOf(state.today.toEpochDay()) }
    var weekMode by rememberSaveable { mutableStateOf(true) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val weekStart = selected.with(TemporalAdjusters.previousOrSame(state.settings.weekStart))
    val week = state.index.upcoming(weekStart, 7)
    val counts = week.associate { it.first to it.second.size }

    ListBody {
        item("title") {
            Column {
                PageTitle("Upcoming", subtitle = selected.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + selected.year)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedEpoch -= 7 }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        week.forEach { (date, _) ->
                            val isSel = date == selected
                            val isToday = date == state.today
                            val n = counts[date] ?: 0
                            Column(
                                Modifier
                                    .heightIn(min = 56.dp)
                                    .background(if (isSel) paper.accentSoft else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable { selectedEpoch = date.toEpochDay() }
                                    .semantics {
                                        role = Role.Tab
                                        this.selected = isSel
                                        contentDescription = Format.longDay(date) + (if (isToday) ", today" else "") + ", $n tasks"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = paper.muted)
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = if (isToday) paper.accent else paper.ink)
                                Box(Modifier.size(5.dp).background(if (n > 0) paper.muted else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
                            }
                        }
                    }
                    IconButton(onClick = { selectedEpoch += 7 }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(selected = !weekMode, onClick = { weekMode = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Day") }
                        SegmentedButton(selected = weekMode, onClick = { weekMode = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Week") }
                    }
                    if (selected != state.today) {
                        TextButton(onClick = { selectedEpoch = state.today.toEpochDay() }) { Text("Today") }
                    }
                }
            }
        }
        val earlier = state.index.today(state.today).earlier
        if (earlier.isNotEmpty() && !state.today.isBefore(weekStart) && state.today.isBefore(weekStart.plusDays(7))) {
            header("Earlier", "earlier")
            taskRows(earlier, state, vm, nav, selection, keyPrefix = "e")
        }
        val days = if (weekMode) week else week.filter { it.first == selected }
        days.forEach { (date, items) ->
            if (date.isBefore(state.today) && items.isEmpty()) return@forEach
            item("d-$date") {
                GroupHeader(Format.day(date, state.today) + if (Format.day(date, state.today) in setOf("Today", "Tomorrow")) " · " + Format.longDay(date) else "") {
                    TextButton(onClick = { nav.quickAdd(QuickAddTarget(date = date)) }) { Text("Add") }
                }
            }
            taskRows(items, state, vm, nav, selection, keyPrefix = "$date-")
            if (items.isEmpty()) item("empty-$date") { Text("—", color = paper.muted, modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)) }
        }
    }
}

