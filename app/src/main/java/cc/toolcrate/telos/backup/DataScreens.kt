package cc.toolcrate.telos.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cc.toolcrate.telos.domain.io.ImportPlan
import cc.toolcrate.telos.domain.io.ImportedTask
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.recurrence.describe
import cc.toolcrate.telos.ui.AppState
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.components.GroupHeader
import cc.toolcrate.telos.ui.screens.BackTitle
import cc.toolcrate.telos.ui.screens.Nav
import cc.toolcrate.telos.ui.theme.LocalPaper
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalPaper.current.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
fun PassphraseDialog(title: String, confirm: Boolean, onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = a.length >= 8 && (!confirm || a == b)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirm) Text("At least 8 characters. Telos can't recover it for you.")
                OutlinedTextField(a, { a = it }, label = { Text("Passphrase") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (confirm) OutlinedTextField(b, { b = it }, label = { Text("Again") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = b.isNotEmpty() && a != b)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onDone(a.toCharArray()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DataScreen(state: AppState, nav: Nav) {
    val vm: DataViewModel = hiltViewModel()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val settings = state.settings
    var dialog by remember { mutableStateOf<String?>(null) }
    var afterAuth by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val writeJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> vm.write(u, "json") } }
    val writeCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.write(u, "csv") } }
    val writeSealed = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let { u -> vm.write(u, "sealed") } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::read) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val token = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(r.data).accessToken }.getOrNull()
            token?.let { t -> afterAuth?.invoke(t) }
        }
    }
    fun askConsent(result: AuthorizationResult, then: (String) -> Unit) {
        afterAuth = then
        result.pendingIntent?.let { consent.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }
    val today = LocalDate.now()

    ui.plan?.let { plan -> return ImportPreview(plan, state, vm, nav) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        BackTitle("Import, export and backup", nav)
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        ui.message?.let { Text(it, modifier = Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }) }

        GroupHeader("Export")
        Para("A full copy you can keep or move. JSON can be imported back into Telos; CSV opens in a spreadsheet. These files are not encrypted.")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { writeJson.launch("telos-${today}.json") }) { Text("JSON") }
            OutlinedButton(onClick = { writeCsv.launch("telos-${today}.csv") }) { Text("CSV") }
        }

        GroupHeader("Import")
        Para("From Todoist (a project's CSV export), a plain-text list, a Telos JSON export or an encrypted backup. You'll see a preview before anything is added.")
        OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Choose a file") }

        GroupHeader("Encrypted backup")
        Para("Backups are sealed on this phone with your passphrase (Argon2id and AES-256-GCM). Without the passphrase they can't be opened by anyone, including you, so keep it somewhere safe.")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = "set" }) { Text(if (vm.vault.hasKey()) "Change passphrase" else "Set passphrase") }
            OutlinedButton(enabled = vm.vault.hasKey(), onClick = { writeSealed.launch("telos-${today}.telos") }) { Text("Save to a file") }
        }

        GroupHeader("Google Drive")
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Back up to my Google Drive")
                Text("Weekly, on Wi-Fi, to a hidden app folder in your own Drive.", style = MaterialTheme.typography.bodySmall, color = LocalPaper.current.muted)
            }
            Switch(checked = settings.driveEnabled, enabled = vm.vault.hasKey(), onCheckedChange = { vm.setDrive(it) })
        }
        if (!vm.vault.hasKey()) Para("Set a passphrase first.")
        if (settings.driveEnabled) {
            settings.lastBackupAt?.let { Para("Last backup: " + Format.day(LocalDate.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()), today)) }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.backUpNow { r -> askConsent(r) { t -> vm.backUpNow(t) {} } }
                }) { Text("Back up now") }
                OutlinedButton(onClick = { dialog = "restore" }) { Text("Restore from Drive") }
            }
            Para("Backing up also merges in changes from your other phones, so they stay in step. Restoring merges too: anything newer on this phone is kept.")
        }
    }

    when (dialog) {
        "set" -> PassphraseDialog("Backup passphrase", confirm = true, onDismiss = { dialog = null }) { vm.setPassphrase(it); dialog = null }
        "restore" -> PassphraseDialog("Passphrase for the Drive backup", confirm = false, onDismiss = { dialog = null }) { p ->
            dialog = null
            vm.restoreFromDrive(p) { r -> askConsent(r) { t -> vm.restoreFromDrive(p, t) {} } }
        }
    }
    if (ui.pendingSealed != null) {
        PassphraseDialog("Passphrase for this backup", confirm = false, onDismiss = vm::cancelPending) { vm.openSealed(it) }
    }
    ui.pendingExport?.let { s ->
        AlertDialog(
            onDismissRequest = vm::cancelPending,
            title = { Text("Merge this copy?") },
            text = { Text("It has ${s.tasks.count { it.deletedAt == null }} tasks, ${s.projects.count { it.deletedAt == null && !it.isInbox }} projects and ${s.goals.count { it.deletedAt == null }} goals. Records are merged one by one; the newer version of each wins, so nothing newer on this phone is lost.") },
            confirmButton = { TextButton(onClick = vm::confirmMerge) { Text("Merge") } },
            dismissButton = { TextButton(onClick = vm::cancelPending) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ImportPreview(plan: ImportPlan, state: AppState, vm: DataViewModel, nav: Nav) {
    val paper = LocalPaper.current
    var target by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(plan.suggestedProject ?: "Imported") }
    var menu by remember { mutableStateOf(false) }
    val projects = listOfNotNull(state.index.projects[Project.INBOX_ID]) + state.index.projectTree().map { it.first }
    val sample = plan.tasks.flatMap { t -> flattenWithDepth(t, 0) }.take(40)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        BackTitle("Import preview", nav)
        Para("From ${plan.source}: ${plan.taskCount} tasks, ${plan.sections.size} sections, ${plan.labels.size} labels, ${plan.dated} with dates, ${plan.recurring} repeating. Nothing is added until you confirm.")
        plan.warnings.forEach { Para("Note: $it") }
        if (plan.source == "Todoist") {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Priorities the other way round?", modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setPlan(plan.flipPriorities()) }) { Text("Flip") }
            }
        }
        GroupHeader("Add to")
        Column(Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = { menu = true }) { Text(target?.let { id -> projects.firstOrNull { it.id == id }?.name } ?: "A new project") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("A new project") }, onClick = { target = null; menu = false })
                projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { target = p.id; menu = false }) }
            }
            if (target == null) OutlinedTextField(name, { name = it }, label = { Text("New project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        GroupHeader("First tasks")
        sample.forEach { (t, depth) ->
            Column(Modifier.padding(start = (20 + 18 * depth).dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
                Text((if (t.done) "✓ " else "○ ") + t.title, style = MaterialTheme.typography.bodyLarge)
                val meta = listOfNotNull(
                    t.section, t.dueDate?.let { Format.day(it, state.today) + (t.dueTime?.let { tm -> " " + Format.time(tm.hour * 60 + tm.minute) } ?: "") },
                    t.recurrence?.describe(), if (t.priority < 4) "P${t.priority}" else null,
                ) + t.labels.map { "@$it" }
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = paper.muted)
            }
        }
        if (plan.taskCount > sample.size) Para("…and ${plan.taskCount - sample.size} more.")
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = vm::cancelPending) { Text("Cancel") }
            Button(enabled = plan.taskCount > 0, onClick = { vm.commitPlan(target, name) { id -> nav.back(); nav.openProject(id) } }) { Text("Import ${plan.taskCount}") }
        }
    }
}

private fun flattenWithDepth(t: ImportedTask, depth: Int): List<Pair<ImportedTask, Int>> =
    listOf(t to depth) + t.children.flatMap { flattenWithDepth(it, depth + 1) }

/** A plain statement of what Telos sends anywhere, kept true by the settings it reads. */
@Composable
fun PrivacyScreen(state: AppState, nav: Nav) {
    val s = state.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        BackTitle("What leaves this device", nav)
        Text(
            if (s.driveEnabled) "Only your encrypted backup, to your own Google Drive." else "Nothing.",
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp),
        )
        if (s.driveEnabled) {
            Para("Once a week on Wi-Fi, and when you tap Back up now, Telos sends one file to a hidden app folder in your Google Drive. It is encrypted on this phone with your passphrase (Argon2id and AES-256-GCM) before it leaves. Google stores it but can't read it. Telos asks Google only for access to its own app folder, not to the rest of your Drive.")
        } else {
            Para("Your tasks, projects, goals and settings stay in this app's private storage on this phone. Telos has no account, no server and no analytics, and it doesn't connect to the internet.")
        }
        GroupHeader("Plainly")
        listOf(
            "No accounts, ads, analytics, tracking or crash reporting.",
            "The internet permission exists for one reason: the optional Google Drive backup. With it off, Telos never connects; one small piece of code (NetworkGate) is the only way out, and it stays shut.",
            "Shared links are kept as text. Telos never fetches a page, title or icon.",
            "Android's own cloud backup and device-to-device transfer are switched off for Telos, so nothing is copied without your say.",
            "Exports (JSON, CSV) are not encrypted; they go only where you save them. Encrypted backups need your passphrase to open.",
            if (s.hideOnLockScreen) "Reminders on the lock screen show only “Telos · 1 reminder”." else "Reminders on the lock screen show the task. You can hide them in Settings.",
            "Tasks with a cover title show it instead of the real title in widgets and notifications.",
            "On the phone, data is protected by Android's storage encryption and app sandbox; Telos doesn't add its own encryption at rest.",
        ).forEach { Para("• $it") }
    }
}
