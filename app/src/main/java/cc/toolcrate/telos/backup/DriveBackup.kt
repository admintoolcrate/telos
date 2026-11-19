package cc.toolcrate.telos.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.TimeSource
import cc.toolcrate.telos.domain.backup.BackupKey
import cc.toolcrate.telos.domain.backup.Envelope
import cc.toolcrate.telos.domain.io.Codec
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What a Drive backup did, in plain words for the screen. */
data class BackupOutcome(val uploaded: Boolean, val mergedIn: Int, val message: String)

class NeedsSignIn(val result: AuthorizationResult) : Exception("Google needs you to allow Drive access.")

/**
 * Encrypted backup to the app's private folder in the user's own Google Drive (appDataFolder:
 * invisible in Drive, readable only by Telos). Each run downloads what's there, merges it in
 * (so two phones converge), and uploads the merged copy, sealed on this phone first.
 */
@Singleton
class DriveBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: NetworkGate,
    private val vault: KeyVault,
    private val transfer: DataTransfer,
    private val prefs: Prefs,
    private val time: TimeSource,
) {
    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** An access token, or [NeedsSignIn] carrying the consent screen to show. */
    suspend fun token(): String {
        val result = Identity.getAuthorizationClient(context).authorize(authorizationRequest()).await()
        if (result.hasResolution()) throw NeedsSignIn(result)
        return result.accessToken ?: throw NeedsSignIn(result)
    }

    suspend fun backUpNow(accessToken: String? = null): BackupOutcome = withContext(Dispatchers.IO) {
        val key = vault.load() ?: return@withContext BackupOutcome(false, 0, "Set a backup passphrase first.")
        val token = accessToken ?: token()
        val auth = mapOf("Authorization" to "Bearer $token")
        val existingId = findFile(auth)
        var merged = 0
        if (existingId != null) {
            val remote = gate.request("GET", "$API/files/$existingId?alt=media", auth)
            val opened = runCatching { Envelope.open(remote, BackupKey(key.bytes, Envelope.header(remote)?.kdf ?: key.kdf)) }
            // A backup made with another passphrase can't be merged; it's replaced only after asking.
            val export = opened.getOrNull()?.let(Codec::decode)
                ?: return@withContext BackupOutcome(false, 0, "The backup in Drive was made with a different passphrase. Restore it with that passphrase, or reset the backup.")
            merged = transfer.merge(export.snapshot).fromIncoming
        }
        val sealed = Envelope.seal(transfer.exportJson(), key, time.now())
        upload(auth, existingId, sealed)
        prefs.update { copy(lastBackupAt = time.now()) }
        BackupOutcome(true, merged, if (merged > 0) "Backed up, and merged $merged changes from another device." else "Backed up.")
    }

    /** Opens the Drive backup with [passphrase] and merges it in. */
    suspend fun restore(passphrase: CharArray, accessToken: String? = null): Int = withContext(Dispatchers.IO) {
        val auth = mapOf("Authorization" to "Bearer ${accessToken ?: token()}")
        val id = findFile(auth) ?: throw java.io.FileNotFoundException("There's no Telos backup in this Google account yet.")
        val file = gate.request("GET", "$API/files/$id?alt=media", auth)
        val export = Codec.decode(Envelope.open(file, passphrase)) ?: throw IllegalStateException("The backup couldn't be read.")
        transfer.merge(export.snapshot).fromIncoming
    }

    /** Deletes the backup from Drive. */
    suspend fun deleteRemote(accessToken: String? = null) = withContext(Dispatchers.IO) {
        val auth = mapOf("Authorization" to "Bearer ${accessToken ?: token()}")
        findFile(auth)?.let { gate.request("DELETE", "$API/files/$it", auth) }
    }

    private suspend fun findFile(auth: Map<String, String>): String? {
        val q = java.net.URLEncoder.encode("name='$FILE_NAME'", "UTF-8")
        val body = gate.request("GET", "$API/files?spaces=appDataFolder&fields=files(id,modifiedTime)&q=$q", auth)
        return Json.parseToJsonElement(body).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private suspend fun upload(auth: Map<String, String>, existingId: String?, content: String) {
        val boundary = "telos" + System.nanoTime()
        val meta = if (existingId == null) """{"name":"$FILE_NAME","parents":["appDataFolder"]}""" else """{"name":"$FILE_NAME"}"""
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n")
            append("--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n$content\r\n--$boundary--")
        }.toByteArray()
        val headers = auth + ("Content-Type" to "multipart/related; boundary=$boundary")
        if (existingId == null) gate.request("POST", "$UPLOAD/files?uploadType=multipart", headers, body)
        else gate.request("PATCH", "$UPLOAD/files/$existingId?uploadType=multipart", headers, body)
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_NAME = "telos-backup.telos"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork("drive"); return }
            wm.enqueueUniquePeriodicWork(
                "drive", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DriveBackupWorker>(7, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }
    }
}

/** The weekly backup. Skips quietly if Drive access needs confirming in the app. */
@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val drive: DriveBackup,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { drive.backUpNow() }.fold(
        onSuccess = { Result.success() },
        onFailure = { if (it is NeedsSignIn || it is NetworkBlocked) Result.success() else Result.retry() },
    )
}

