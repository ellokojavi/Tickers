package cl.ufchile.app.data.repo

import android.content.Context
import cl.ufchile.app.data.local.dao.IndicatorDao
import cl.ufchile.app.data.local.dao.UfDao
import cl.ufchile.app.data.local.entity.IndicatorEntity
import cl.ufchile.app.data.local.entity.UfValueEntity
import cl.ufchile.app.data.prefs.SettingsStore
import cl.ufchile.app.data.remote.UfRemoteDataSource
import cl.ufchile.app.data.seed.UfSeed
import cl.ufchile.app.domain.model.DataSource
import cl.ufchile.app.domain.model.Indicator
import cl.ufchile.app.domain.model.UfValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    suspend fun ufOn(date: LocalDate): UfValue? =
        (ufDao.byDate(date) ?: ufDao.atOrBefore(date))?.let { UfValue(it.date, it.value) }

    suspend fun isEmpty(): Boolean = ufDao.count() == 0

    /**
     * CPI index anchors: the bundled series overlaid with anything newer that
     * has since been downloaded.
     */
    suspend fun anchors(): Map<YearMonth, BigDecimal> {
        val seed = UfSeed.anchors(context).toMutableMap()
        ufDao.monthAnchors().forEach { e -> seed[YearMonth.from(e.date)] = e.value }
        return seed
    }

    /**
     * Refreshes the cache. Tries each configured source in order and returns
     * the one that succeeded, or a failure carrying the last error.
     */
    suspend fun sync(years: List<Int> = defaultYears()): Result<DataSource> {
        var lastError: Throwable? = null
        for (source in sources) {
            if (!source.available) continue
            try {
                val all = mutableListOf<UfValueEntity>()
                for (year in years) {
                    source.ufForYear(year).forEach {
                        all += UfValueEntity(it.date, it.value, source.source.name)
                    }
                }
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

    private fun defaultYears(): List<Int> {
        val now = LocalDate.now().year
        return listOf(now - 1, now)
    }
}
