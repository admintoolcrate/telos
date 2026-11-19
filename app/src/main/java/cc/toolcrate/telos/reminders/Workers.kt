package cc.toolcrate.telos.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cc.toolcrate.telos.data.Repository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Safety net: re-reconciles alarms every few hours, in case anything slipped. */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        scheduler.reconcile()
        return Result.success()
    }
}

/** Empties trash older than 30 days, once a day. */
@HiltWorker
class TrashPurgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: Repository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        repo.purgeExpiredTrash()
        return Result.success()
    }
}

object Background {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "reconcile", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReconcileWorker>(6, TimeUnit.HOURS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "trash", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
