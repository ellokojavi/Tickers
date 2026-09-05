package cl.ufchile.app.ui.value

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.data.prefs.SettingsStore
import cl.ufchile.app.data.prefs.SyncInfo
import cl.ufchile.app.data.repo.UfRepository
import cl.ufchile.app.domain.engine.LookupResult
import cl.ufchile.app.domain.engine.UfEngine
import cl.ufchile.app.domain.engine.UfLookup
import cl.ufchile.app.domain.model.Indicator
import cl.ufchile.app.domain.model.UfValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

enum class Range(val label: String, val months: Long?) {
    M1("1M", 1), M3("3M", 3), M6("6M", 6),
    Y1("1A", 12), Y5("5A", 60), MAX("Máx", null),
}

/**
 * State of the single "Valor UF" screen.
 *
 * Today's value and the historical series are one subject, not two, so they
 * live in one state object: the screen shows today's value and the series that
 * produced it, always, with no mode to toggle between them.
 */
data class UfUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val current: UfValue? = null,
    val previous: UfValue? = null,
    val monthAgo: UfValue? = null,
    val future: List<UfValue> = emptyList(),
    val indicators: List<Indicator> = emptyList(),
    val sync: SyncInfo = SyncInfo(null, 0L),
    val error: String? = null,
    val ufText: String = "1",
    val clpText: String = "",

    // --- history
    val range: Range = Range.M3,
    val all: List<UfValue> = emptyList(),

    /**
     * Every day in the selected window. Carried as state rather than computed
     * in a getter: the full series is ~18.000 days, and a getter would re-filter
     * it on every pointer event while scrubbing.
     */
    val rangeValues: List<UfValue> = emptyList(),

    /** [rangeValues] thinned to what a chart can actually draw. */
    val chartValues: List<UfValue> = emptyList(),

    val scrubIndex: Int? = null,
    val lookupDate: LocalDate = LocalDate.now(),
    val lookup: LookupResult = LookupResult.Loading,
    val loadingOlder: Boolean = false,
    val historyMessage: String? = null,
    /** The full day-by-day list is long, so it starts folded away. */
    val detailExpanded: Boolean = false,
) {
    val dailyDelta: BigDecimal?
        get() = if (current != null && previous != null)
            UfEngine.delta(previous.value, current.value) else null

    val monthDeltaPct: BigDecimal?
        get() = if (current != null && monthAgo != null)
            UfEngine.deltaPct(monthAgo.value, current.value) else null

    /** Last day with a published value; the newest date a lookup may ask about. */
    val lastPublished: LocalDate?
        get() = UfLookup.maxSelectable(all)

    /** The day the finger is on, if any. Never replaces [current] in the UI. */
    val scrubbed: UfValue?
        get() = scrubIndex?.let { chartValues.getOrNull(it) }

    val rangeChangePct: BigDecimal?
        get() {
            val first = rangeValues.firstOrNull() ?: return null
            val last = rangeValues.lastOrNull() ?: return null
            return UfEngine.deltaPct(first.value, last.value)
        }

    /** The same movement expressed as a constant annual rate. */
    val rangeAnnualisedPct: BigDecimal?
        get() {
            val first = rangeValues.firstOrNull() ?: return null
            val last = rangeValues.lastOrNull() ?: return null
            val days = java.time.temporal.ChronoUnit.DAYS.between(first.date, last.date)
            if (days <= 0L) return null
            return UfEngine.annualisedPct(first.value, last.value, days)
        }
}

class UfViewModel(
    private val repo: UfRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(UfUiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.observeSeries(),
                repo.observeIndicators(),
                settings.syncInfo,
            ) { series, indicators, sync -> Triple(series, indicators, sync) }
                .collect { (series, indicators, sync) -> project(series, indicators, sync) }
        }
        viewModelScope.launch {
            // The bundled series lands first so the charts are complete before
            // the network is even reachable.
            repo.ensureSeeded()
            lookup(_ui.value.lookupDate)
            refresh(force = false)
        }
    }

    private companion object {
        /** More points than this cannot be resolved on a phone-width chart. */
        const val MAX_CHART_POINTS = 400
    }

    private fun project(series: List<UfValue>, indicators: List<Indicator>, sync: SyncInfo) {
        val today = LocalDate.now()
        val current = UfEngine.currentOf(series, today)
        val previous = current?.let { c -> series.filter { it.date < c.date }.maxByOrNull { it.date } }
        val monthAgo = current?.let { c ->
            val target = c.date.minusMonths(1)
            series.filter { !it.date.isAfter(target) }.maxByOrNull { it.date }
        }
        val windowed = window(series, _ui.value.range)
        _ui.value = _ui.value.copy(
            loading = false,
            current = current,
            previous = previous,
            monthAgo = monthAgo,
            future = UfEngine.futureOf(series, today),
            all = series,
            rangeValues = windowed,
            chartValues = UfEngine.downsample(windowed, MAX_CHART_POINTS),
            indicators = indicators,
            sync = sync,
            // The converter deals in whole pesos: Chile has not used centavos
            // for decades. The headline UF value keeps its two decimals because
            // the UF itself is published that way.
            clpText = _ui.value.clpText.ifBlank {
                current?.let { Fmt.clp(it.value).removePrefix("$") }.orEmpty()
            },
        )
    }

    /** [force] is set by the refresh button; the launch sync lets it coalesce. */
    fun refresh(force: Boolean = true) {
        if (_ui.value.refreshing) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(refreshing = true, error = null)
            val result = repo.sync(force = force)
            _ui.value = _ui.value.copy(
                refreshing = false,
                loading = false,
                error = result.exceptionOrNull()?.let {
                    "No se pudo actualizar. Mostrando datos guardados."
                },
            )
        }
    }

    // ------------------------------------------------------------ converter

    fun onUfInput(text: String) {
        val uf = Fmt.parseNumber(text)
        val rate = _ui.value.current?.value
        _ui.value = _ui.value.copy(
            ufText = text,
            clpText = if (uf != null && rate != null)
                Fmt.clp(UfEngine.ufToClp(uf, rate)).removePrefix("$") else "",
        )
    }

    fun onClpInput(text: String) {
        val clp = Fmt.parseNumber(text)
        val rate = _ui.value.current?.value
        _ui.value = _ui.value.copy(
            clpText = text,
            ufText = if (clp != null && rate != null)
                UfEngine.clpToUf(clp, rate).toPlainString().replace('.', ',') else "",
        )
    }

    // -------------------------------------------------------------- history

    fun toggleDetail() {
        _ui.value = _ui.value.copy(detailExpanded = !_ui.value.detailExpanded)
    }

    fun setRange(range: Range) {
        val windowed = window(_ui.value.all, range)
        _ui.value = _ui.value.copy(
            range = range,
            scrubIndex = null,
            rangeValues = windowed,
            chartValues = UfEngine.downsample(windowed, MAX_CHART_POINTS),
        )
        val needed = range.months?.let { LocalDate.now().minusMonths(it).year }
            ?: _ui.value.all.minOfOrNull { it.date.year }
        if (needed != null) ensureFrom(needed)
    }

    fun scrub(index: Int?) {
        _ui.value = _ui.value.copy(scrubIndex = index)
    }

    fun lookup(date: LocalDate) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(lookupDate = date, lookup = LookupResult.Loading)
            val today = LocalDate.now()

            // Coverage is decided before any value is fetched, so an
            // out-of-range date can never be answered with a nearby day.
            val bounds = UfLookup.resolve(
                requested = date,
                exact = null,
                nearestEarlier = null,
                lastPublished = _ui.value.lastPublished,
                today = today,
            )
            if (bounds is LookupResult.BeforeCoverage || bounds is LookupResult.NotPublishedYet) {
                _ui.value = _ui.value.copy(lookup = bounds)
                return@launch
            }

            if (_ui.value.all.none { it.date.year == date.year }) {
                downloadMissingYears(date.year)
            }

            _ui.value = _ui.value.copy(
                lookup = UfLookup.resolve(
                    requested = date,
                    exact = repo.exactUfOn(date),
                    nearestEarlier = repo.nearestUfOn(date),
                    lastPublished = _ui.value.lastPublished,
                    today = today,
                )
            )
        }
    }

    /** The slice of the series the selected range covers. */
    private fun window(series: List<UfValue>, range: Range): List<UfValue> {
        val months = range.months ?: return series
        val from = LocalDate.now().minusMonths(months)
        return series.filter { !it.date.isBefore(from) }
    }

    private fun ensureFrom(year: Int) {
        viewModelScope.launch { downloadMissingYears(year) }
    }

    /** Downloads any year in [year]..now that the cache does not hold yet. */
    private suspend fun downloadMissingYears(year: Int) {
        val missing = (year..LocalDate.now().year)
            .filter { y -> _ui.value.all.none { it.date.year == y } }
        if (missing.isEmpty()) return
        _ui.value = _ui.value.copy(loadingOlder = true, historyMessage = null)
        var failed = false
        missing.forEach { y -> if (repo.ensureYear(y).isFailure) failed = true }
        _ui.value = _ui.value.copy(
            loadingOlder = false,
            historyMessage = if (failed)
                "Faltan años que no se pudieron descargar." else null,
        )
    }
}
