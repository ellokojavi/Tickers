package cl.tickers.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cl.tickers.app.TickersApp

/** Daily background refresh of the UF cache. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TickersApp ?: return Result.failure()
        return app.container.ufRepository.sync().fold(
            onSuccess = { Result.success() },
            // Retry rather than fail: the cache stays valid meanwhile.
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() },
        )
    }

    companion object {
        const val NAME = "uf-daily-sync"
    }
}
