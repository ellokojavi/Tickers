package cl.ufchile.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cl.ufchile.app.di.AppContainer
import cl.ufchile.app.work.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * WorkManager uses on-demand initialisation (its default startup initializer is
 * removed in the manifest) so that the app does not depend on a ContentProvider
 * having run before [onCreate]. Background sync is a convenience; it must never
 * be able to stop the app from starting.
 */
class UfChileApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleDailySync()
    }

    /**
     * One refresh a day is enough: the UF for the whole current period is
     * already published, so there is nothing new to learn more often.
     */
    private fun scheduleDailySync() {
        // Never let a scheduling failure surface as a crash on launch.
        runCatching {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SyncWorker.NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
