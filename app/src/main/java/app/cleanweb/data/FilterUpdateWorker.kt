package app.cleanweb.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cleanweb.AppGraph
import java.util.concurrent.TimeUnit

/** Refreshes filter lists once a day and trims the query log. */
class FilterUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val g = AppGraph.get(applicationContext)
        g.log.purgeOlderThan(g.settings.current.logRetentionDays)
        val failed = g.filters.updateAll()
        return if (failed > 0 && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "filter-update"

        fun schedule(context: Context, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<FilterUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
