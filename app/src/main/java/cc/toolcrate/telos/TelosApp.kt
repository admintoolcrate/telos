package cc.toolcrate.telos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import cc.toolcrate.telos.data.Repository
import cc.toolcrate.telos.di.AppScope
import cc.toolcrate.telos.reminders.Background
import cc.toolcrate.telos.reminders.Notifier
import cc.toolcrate.telos.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TelosApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var repo: Repository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: Notifier
    @Inject @AppScope lateinit var scope: CoroutineScope

    /** Hooks that later features (widgets) add, run on every data change. */
    @Inject lateinit var changeHooks: Set<@JvmSuppressWildcards ChangeHook>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        Background.schedule(this)
        scope.launch {
            repo.ensureInbox()
            // Every change to tasks or reminders re-plans alarms (and refreshes widgets).
            repo.snapshot
                .map { it.tasks to it.reminders }
                .distinctUntilChanged()
                .debounce(400)
                .collect {
                    runCatching { scheduler.reconcile() }
                    changeHooks.forEach { h -> runCatching { h.onDataChanged() } }
                }
        }
    }
}

/** Something to refresh when data changes (widgets, tiles). Bound into a set with Hilt. */
fun interface ChangeHook {
    suspend fun onDataChanged()
}
