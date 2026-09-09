package cl.tickers.app.di

import android.content.Context
import cl.tickers.app.BuildConfig
import cl.tickers.app.data.local.AppDatabase
import cl.tickers.app.data.prefs.SettingsStore
import cl.tickers.app.data.remote.BtcRemoteDataSource
import cl.tickers.app.data.remote.CmfDataSource
import cl.tickers.app.data.remote.MindicadorDataSource
import cl.tickers.app.data.remote.NetworkModule
import cl.tickers.app.data.repo.SimulationRepository
import cl.tickers.app.data.repo.UfRepository

/**
 * Manual dependency graph.
 *
 * The app has a single, shallow object graph, so a hand-written container is
 * cheaper to reason about than an annotation processor and removes a whole
 * class of build failures.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    private val okHttp by lazy { NetworkModule.okHttp() }

    /**
     * Preference order: the official CMF API first, mindicador.cl as fallback.
     * CmfDataSource reports itself unavailable when no key is configured, so a
     * build without a key silently uses the secondary source.
     */
    private val sources by lazy {
        listOf(
            CmfDataSource(NetworkModule.cmfApi(okHttp), BuildConfig.CMF_API_KEY),
            MindicadorDataSource(NetworkModule.mindicadorApi(okHttp)),
        )
    }

    val ufRepository: UfRepository by lazy {
        UfRepository(
            context = appContext,
            ufDao = database.ufDao(),
            indicatorDao = database.indicatorDao(),
            settings = settings,
            sources = sources,
        )
    }

    /**
     * No repository behind it: a bitcoin price is worth nothing a minute later,
     * so there is nothing to cache and nothing to reconcile. The UF has a
     * repository because its values are permanent once published.
     */
    val btcSource: BtcRemoteDataSource by lazy { BtcRemoteDataSource(okHttp) }

    val simulationRepository: SimulationRepository by lazy {
        SimulationRepository(database.simulationDao())
    }
}
