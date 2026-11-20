package cc.toolcrate.telos.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.data.Settings
import cc.toolcrate.telos.reminders.ReminderAccess
import cc.toolcrate.telos.reminders.describeBefore
import cc.toolcrate.telos.security.AppLock
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.theme.LocalPaper
import cc.toolcrate.telos.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun ToggleRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled) { onChange(!checked) }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalPaper.current.muted) }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun <T> ChoiceRow(title: String, current: T, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == current }?.second.orEmpty()
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = true }
                .semantics(mergeDescendants = true) { role = Role.DropdownList; contentDescription = "$title: $label" }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, color = LocalPaper.current.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onPick(v); open = false }) }
        }
    }
}

@Composable
fun SettingsScreen(state: AppState, vm: AppViewModel, nav: Nav, lock: AppLock) {
    val s = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pinDialog by remember { mutableStateOf(false) }
    fun set(change: Settings.() -> Settings) = vm.setTheme(change)
    val scheduler = remember { ReminderAccess.scheduler(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        BackTitle("Settings", nav)

        GroupHeader("Appearance")
        ChoiceRow("Theme", s.theme, listOf(ThemeMode.System to "Follow the phone", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark")) { set { copy(theme = it) } }
        ToggleRow("Reduce motion", "Also follows the phone's “Remove animations”.", s.reduceMotion) { set { copy(reduceMotion = it) } }

        GroupHeader("Tasks")
        ChoiceRow("Week starts on", s.weekStart, listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).map { it to it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }) { set { copy(weekStart = it) } }
        ChoiceRow("Open on", s.startTab, listOf("today" to "Today", "upcoming" to "Upcoming", "inbox" to "Inbox", "goals" to "Goals")) { set { copy(startTab = it) } }
        ChoiceRow("Default reminder", s.defaultReminder, listOf<Pair<Int?, String>>(null to "None") + listOf(0, 10, 30, 60).map { it to describeBefore(it) }) { set { copy(defaultReminder = it) } }

        GroupHeader("Notifications")
        ToggleRow("Hide on the lock screen", "Show only “Telos · 1 reminder” until the phone is unlocked.", s.hideOnLockScreen) { set { copy(hideOnLockScreen = it) } }
        if (scheduler?.exactAllowed() == false && Build.VERSION.SDK_INT >= 31) {
            TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) },
                modifier = Modifier.padding(start = 8.dp)) { Text("Allow exact alarms, so reminders are on time") }
        }

        GroupHeader("Privacy")
        val canLock = lock.deviceCanAuthenticate() || lock.hasPin()
        ToggleRow(
            "App lock",
            if (canLock) "Ask for your fingerprint, face, screen lock or Telos PIN." else "Set a PIN, or a screen lock on the phone, first.",
            s.appLock, enabled = canLock,
        ) { on -> set { copy(appLock = on) } }
        TextButton(onClick = { pinDialog = true }, modifier = Modifier.padding(start = 8.dp)) { Text(if (lock.hasPin()) "Change Telos PIN" else "Set a Telos PIN") }
        if (lock.hasPin()) TextButton(onClick = { lock.clearPin() }, modifier = Modifier.padding(start = 8.dp)) { Text("Remove PIN") }
        if (s.appLock) ChoiceRow("Lock after", s.lockTimeoutSeconds, listOf(0 to "Immediately", 60 to "1 minute", 300 to "5 minutes", 1800 to "30 minutes")) { set { copy(lockTimeoutSeconds = it) } }
        ToggleRow("Hide from recent apps and screenshots", null, s.secureScreen) { set { copy(secureScreen = it) } }
        ToggleRow("Cover titles in the app too", "Tasks and goals with a cover title show it everywhere, not just in widgets and notifications.", s.privateInApp) { set { copy(privateInApp = it) } }
        LinkRow("What leaves this device") { nav.go("privacy") }
        LinkRow("Import, export and backup") { nav.go("data") }

        GroupHeader("About")
        LinkRow("Licences", sub = "EB Garamond and Inter, SIL Open Font License") { nav.go("licences") }
        Text("Telos is free. No accounts, no ads, no tracking.", color = LocalPaper.current.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(20.dp))
    }

    if (pinDialog) {
        var a by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("Telos PIN") },
            text = {
                Column {
                    Text("4 to 12 digits.")
                    OutlinedTextField(a, { if (it.all(Char::isDigit) && it.length <= 12) a = it }, label = { Text("PIN") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    OutlinedTextField(b, { if (it.all(Char::isDigit) && it.length <= 12) b = it }, label = { Text("Again") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
            },
            confirmButton = {
                TextButton(enabled = a.length >= 4 && a == b, onClick = {
                    scope.launch { lock.setPin(a); vm.notify("PIN set") }
                    pinDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun LicencesScreen(nav: Nav) {
    val context = LocalContext.current
    val texts = remember {
        listOf("eb-garamond-OFL.txt" to "EB Garamond", "inter-OFL.txt" to "Inter").map { (f, n) ->
            n to runCatching { context.assets.open("licenses/$f").bufferedReader().readText() }.getOrDefault("")
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        BackTitle("Licences", nav)
        texts.forEach { (name, text) ->
            GroupHeader(name)
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}
