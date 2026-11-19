package cc.toolcrate.telos.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.TimeSource
import cc.toolcrate.telos.domain.backup.Envelope
import cc.toolcrate.telos.domain.backup.WrongPassphrase
import cc.toolcrate.telos.domain.io.Codec
import cc.toolcrate.telos.domain.io.ImportPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DataUi(
    val busy: Boolean = false,
    val message: String? = null,
    /** An import waiting for the user's yes in the preview. */
    val plan: ImportPlan? = null,
    /** A merge waiting for the user's yes (a Telos export). */
    val pendingExport: cc.toolcrate.telos.domain.model.Snapshot? = null,
    /** An encrypted backup file waiting for its passphrase. */
    val pendingSealed: String? = null,
    /** What the next file picker is for. */
    val sealing: Boolean = false,
)

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val transfer: DataTransfer,
    val drive: DriveBackup,
    val vault: KeyVault,
    val prefs: Prefs,
    private val time: TimeSource,
) : ViewModel() {
    private val _ui = MutableStateFlow(DataUi())
    val ui: StateFlow<DataUi> = _ui.asStateFlow()

    private fun busy(message: String? = null, block: suspend () -> String?) = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = message) }
        val result = runCatching { block() }.getOrElse { e -> e.message ?: "Something went wrong." }
        _ui.update { it.copy(busy = false, message = result) }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun write(uri: Uri, what: String) = busy("Writing…") {
        val text = when (what) {
            "json" -> transfer.exportJson()
            "csv" -> transfer.exportCsv()
            else -> {
                val key = vault.load() ?: return@busy "Set a backup passphrase first."
                Envelope.seal(transfer.exportJson(), key, time.now())
            }
        }
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
        "Saved."
    }

    fun read(uri: Uri) = busy("Reading…") {
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
        when (val incoming = transfer.identify(text, name)) {
            is Incoming.EncryptedBackup -> { _ui.update { it.copy(pendingSealed = text) }; null }
            is Incoming.TelosExport -> { _ui.update { it.copy(pendingExport = incoming.snapshot) }; null }
            is Incoming.Plan -> { _ui.update { it.copy(plan = incoming.plan) }; null }
        }
    }

    fun openSealed(passphrase: CharArray) = busy("Opening…") {
        val file = _ui.value.pendingSealed ?: return@busy null
        val text = try {
            withContext(Dispatchers.Default) { Envelope.open(file, passphrase) }
        } catch (e: WrongPassphrase) {
            return@busy e.message
        }
        val export = Codec.decode(text) ?: return@busy "The backup couldn't be read."
        _ui.update { it.copy(pendingSealed = null, pendingExport = export.snapshot) }
        null
    }

    fun confirmMerge() = busy("Merging…") {
        val s = _ui.value.pendingExport ?: return@busy null
        val r = transfer.merge(s)
        _ui.update { it.copy(pendingExport = null) }
        "Merged: ${r.fromIncoming} records added or updated. Anything newer on this phone was kept."
    }

    fun setPlan(plan: ImportPlan?) = _ui.update { it.copy(plan = plan) }

    fun commitPlan(targetProjectId: String?, newName: String?, done: (String) -> Unit) = busy("Importing…") {
        val plan = _ui.value.plan ?: return@busy null
        val project = transfer.commit(plan, targetProjectId, newName)
        _ui.update { it.copy(plan = null) }
        done(project.id)
        "Imported ${plan.taskCount} tasks into ${project.name}."
    }

    fun cancelPending() = _ui.update { it.copy(plan = null, pendingExport = null, pendingSealed = null) }

    /** Derives the key from a new passphrase (Argon2id, a second or two) and keeps it in the vault. */
    fun setPassphrase(passphrase: CharArray) = busy("Preparing the key…") {
        val key = withContext(Dispatchers.Default) { Envelope.deriveKey(passphrase) }
        passphrase.fill(' ')
        vault.save(key)
        prefs.update { copy(backupEnabled = true) }
        "Passphrase set. Keep it somewhere safe: without it, no one can open your backups, including you."
    }

    fun setDrive(enabled: Boolean) = viewModelScope.launch {
        prefs.update { copy(driveEnabled = enabled) }
        DriveBackup.schedule(context, enabled)
    }

    fun backUpNow(token: String? = null, onNeedsSignIn: (com.google.android.gms.auth.api.identity.AuthorizationResult) -> Unit) = busy("Backing up…") {
        try {
            drive.backUpNow(token).message
        } catch (e: NeedsSignIn) {
            onNeedsSignIn(e.result)
            null
        }
    }

    fun restoreFromDrive(passphrase: CharArray, token: String? = null, onNeedsSignIn: (com.google.android.gms.auth.api.identity.AuthorizationResult) -> Unit) = busy("Restoring…") {
        try {
            "Merged ${drive.restore(passphrase, token)} records from Drive."
        } catch (e: NeedsSignIn) {
            onNeedsSignIn(e.result); null
        } catch (e: WrongPassphrase) {
            e.message
        }
    }
}
