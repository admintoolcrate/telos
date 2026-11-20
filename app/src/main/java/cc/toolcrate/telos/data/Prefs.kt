package cc.toolcrate.telos.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.toolcrate.telos.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

private val Context.store: DataStore<Preferences> by preferencesDataStore("settings")

/** Device settings. Not synced, and not part of backups. */
data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val startTab: String = "today",
    /** Minutes before the due time for new timed tasks; null = none. */
    val defaultReminder: Int? = null,
    val reduceMotion: Boolean = false,
    /** Notifications show "Telos · 1 reminder" on the lock screen instead of the task. */
    val hideOnLockScreen: Boolean = true,
    val appLock: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val secureScreen: Boolean = false,
    /** Show cover titles inside the app too while the app is locked/unlocked. */
    val privateInApp: Boolean = false,
    val backupEnabled: Boolean = false,
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastReviewQuarter: String? = null,
)

@Singleton
class Prefs @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val weekStart = intPreferencesKey("weekStart")
        val startTab = stringPreferencesKey("startTab")
        val defaultReminder = intPreferencesKey("defaultReminder")
        val reduceMotion = booleanPreferencesKey("reduceMotion")
        val hideOnLockScreen = booleanPreferencesKey("hideOnLockScreen")
        val appLock = booleanPreferencesKey("appLock")
        val lockTimeout = intPreferencesKey("lockTimeout")
        val secureScreen = booleanPreferencesKey("secureScreen")
        val privateInApp = booleanPreferencesKey("privateInApp")
        val backupEnabled = booleanPreferencesKey("backupEnabled")
        val driveEnabled = booleanPreferencesKey("driveEnabled")
        val lastBackupAt = stringPreferencesKey("lastBackupAt")
        val lastReviewQuarter = stringPreferencesKey("lastReviewQuarter")
    }

    val settings: Flow<Settings> = context.store.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(change: Settings.() -> Settings) {
        context.store.edit { p ->
            val s = change(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.weekStart] = s.weekStart.value
            p[K.startTab] = s.startTab
            p[K.defaultReminder] = s.defaultReminder ?: -1
            p[K.reduceMotion] = s.reduceMotion
            p[K.hideOnLockScreen] = s.hideOnLockScreen
            p[K.appLock] = s.appLock
            p[K.lockTimeout] = s.lockTimeoutSeconds
            p[K.secureScreen] = s.secureScreen
            p[K.privateInApp] = s.privateInApp
            p[K.backupEnabled] = s.backupEnabled
            p[K.driveEnabled] = s.driveEnabled
            if (s.lastBackupAt != null) p[K.lastBackupAt] = s.lastBackupAt.toString() else p.remove(K.lastBackupAt)
            if (s.lastReviewQuarter != null) p[K.lastReviewQuarter] = s.lastReviewQuarter else p.remove(K.lastReviewQuarter)
        }
    }

    private fun Preferences.toSettings() = Settings(
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
        weekStart = this[K.weekStart]?.let { DayOfWeek.of(it) } ?: DayOfWeek.MONDAY,
        startTab = this[K.startTab] ?: "today",
        defaultReminder = this[K.defaultReminder]?.takeIf { it >= 0 },
        reduceMotion = this[K.reduceMotion] ?: false,
        hideOnLockScreen = this[K.hideOnLockScreen] ?: true,
        appLock = this[K.appLock] ?: false,
        lockTimeoutSeconds = this[K.lockTimeout] ?: 60,
        secureScreen = this[K.secureScreen] ?: false,
        privateInApp = this[K.privateInApp] ?: false,
        backupEnabled = this[K.backupEnabled] ?: false,
        driveEnabled = this[K.driveEnabled] ?: false,
        lastBackupAt = this[K.lastBackupAt]?.toLongOrNull(),
        lastReviewQuarter = this[K.lastReviewQuarter],
    )
}
