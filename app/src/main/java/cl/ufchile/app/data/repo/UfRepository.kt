package cl.ufchile.app.data.repo

import android.content.Context
import cl.ufchile.app.data.local.dao.IndicatorDao
import cl.ufchile.app.data.local.dao.UfDao
import cl.ufchile.app.data.local.entity.IndicatorEntity
import cl.ufchile.app.data.local.entity.UfValueEntity
import cl.ufchile.app.data.prefs.SettingsStore
import cl.ufchile.app.data.remote.UfRemoteDataSource
import cl.ufchile.app.data.seed.UfDailySeed
import cl.ufchile.app.domain.model.DataSource
import cl.ufchile.app.domain.model.Indicator
import cl.ufchile.app.domain.engine.UfSanity
import cl.ufchile.app.domain.model.UfValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Cache-first access to the UF series.
 *
 * Reads always come from Room, so every screen renders offline. The network is
 * only ever a way to refresh that cache, and a failed refresh degrades to a
 * visible staleness warning rather than an empty screen.
 */
class UfRepository(
    private val context: Context,
    private val ufDao: UfDao,
    private val indicatorDao: IndicatorDao,
    private val settings: SettingsStore,
    /** Ordered by preference: the first available one that answers wins. */
    private val sources: List<UfRemoteDataSource>,
) {
    fun observeSeries(): Flow<List<UfValue>> =
        ufDao.observeAll().map { list -> list.map { UfValue(it.date, it.value) } }

    fun observeIndicators(): Flow<List<Indicator>> =
        indicatorDao.observeAll().map { list ->
            list.map { Indicator(it.code, it.name, it.unit, it.date, it.value) }
        }

    /** The value published for exactly [date], or null. Never a substitute. */
    suspend fun exactUfOn(date: LocalDate): UfValue? =
        ufDao.byDate(date)?.let { UfValue(it.date, it.value) }

    /** The closest published day at or before [date]. */
    suspend fun nearestUfOn(date: LocalDate): UfValue? =
        ufDao.atOrBefore(date)?.let { UfValue(it.date, it.value) }

    /**
     * Convenience read used where a substitute is acceptable, such as pricing
     * a simulation in pesos. Date lookups must not use this.
     */
    suspend fun ufOn(date: LocalDate): UfValue? = exactUfOn(date) ?: nearestUfOn(date)

    suspend fun isEmpty(): Boolean = ufDao.count() == 0

    /** Newest day with a published value, future days included. */
    suspend fun lastPublishedDate(): LocalDate? = ufDao.maxDate()

    /**
     * Loads the bundled daily series into the database the first time it is
     * needed. Inserted with IGNORE, so any day already fetched from an API
     * keeps its value; the seed only ever fills gaps.
     *
     * Idempotent and cheap to call: after the first run the row count already
     * covers the seed and it returns immediately.
     */
    suspend fun ensureSeeded(): Int {
        val seed = if (ufDao.count() >= SEED_MIN_ROWS) emptyList() else UfDailySeed.readAll(context)
        if (seed.isEmpty()) return 0
        ufDao.insertMissing(
            seed.map { UfValueEntity(it.date, it.value, DataSource.BUNDLED.name) }
        )
        return seed.size
    }

    /**
     * CPI index anchors: the bundled series overlaid with anything newer that
     * has since been downloaded.
     */
    suspend fun anchors(): Map<YearMonth, BigDecimal> {
        val fromDb = ufDao.monthAnchors()
            .associate { YearMonth.from(it.date) to it.value }
        // Before seeding finishes the database has nothing to offer, so the
        // asset answers directly and the calculator still works on first launch.
        if (fromDb.size < 100) return UfDailySeed.anchors(context) + fromDb
        return fromDb
    }

    /**
     * Refreshes the cache. Tries each configured source in order and returns
     * the one that succeeded, or a failure carrying the last error.
     *
     * Calls are serialised and coalesced: the periodic worker and the screen
     * both refresh at launch, and without this they issue every request twice.
     * [force] skips the coalescing so a manual pull always feels responsive.
     */
    suspend fun sync(years: List<Int>? = null, force: Boolean = false): Result<DataSource> =
        syncMutex.withLock { syncLocked(years, force) }

    private suspend fun syncLocked(years: List<Int>?, force: Boolean): Result<DataSource> {
        val now = System.currentTimeMillis()
        val recent = lastSuccess
        if (!force && years == null && recent != null && now - lastSuccessAt < COALESCE_WINDOW_MS) {
            return Result.success(recent)
        }

        // Resolved here rather than as a default argument: working out which
        // years are missing needs a database read, and a default cannot suspend.
        val targets = years ?: yearsToRefresh()
        var lastError: Throwable? = null
        for (source in sources) {
            if (!source.available) continue
            try {
                val fetched = mutableListOf<UfValue>()
                for (year in targets) fetched += source.ufForYear(year)

                // The feed has served corrupt values before, so nothing is
                // stored without a plausibility check against what is already
                // known to be good.
                val anchor = fetched.minByOrNull { it.date }
                    ?.let { nearestUfOn(it.date.minusDays(1)) }
                val all = UfSanity.filter(fetched, anchor)
                    .map { UfValueEntity(it.date, it.value, source.source.name) }
                if (all.isEmpty()) {
                    lastError = IllegalStateException("${source.source.label} no devolvió datos")
                    continue
                }
                ufDao.upsertAll(all)

                runCatching { source.indicators() }.getOrNull()?.let { list ->
                    if (list.isNotEmpty()) {
                        indicatorDao.upsertAll(
                            list.map { IndicatorEntity(it.code, it.name, it.unit, it.date, it.value) }
                        )
                    }
                }
                settings.recordSync(source.source, System.currentTimeMillis())
                lastSuccess = source.source
                lastSuccessAt = System.currentTimeMillis()
                return Result.success(source.source)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Sin fuentes disponibles"))
    }

    /** Pulls an older year on demand, when the user scrolls back that far. */
    suspend fun ensureYear(year: Int): Result<Unit> {
        if (ufDao.yearsPresent().contains(year)) return Result.success(Unit)
        return sync(listOf(year)).map { }
    }

    /**
     * Only the years the cache does not already cover. With the full series
     * bundled that is normally just the current one, so a refresh is a single
     * request instead of re-downloading history the app already has.
     */
    private suspend fun yearsToRefresh(): List<Int> {
        val now = LocalDate.now().year
        val last = ufDao.maxDate()?.year ?: return listOf(now)
        return (last..now).toList()
    }

    private val syncMutex = Mutex()

    @Volatile private var lastSuccess: DataSource? = null
    @Volatile private var lastSuccessAt = 0L

    private companion object {
        /** Well under the real seed size, so a partial insert still re-runs. */
        const val SEED_MIN_ROWS = 17_000

        /**
         * The UF changes once a day, so refreshes closer together than this
         * cannot learn anything new.
         */
        const val COALESCE_WINDOW_MS = 60_000L
    }
}
