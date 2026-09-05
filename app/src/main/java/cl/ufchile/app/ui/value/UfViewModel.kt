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
 * live in one state object. The history controls are opt-in via [historyOpen];
 * everything above that flag renders identically whether it is set or not, so
 * opening the app is never slower for the common case.
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

    // --- history, revealed on demand
    val historyOpen: Boolean = false,
    val range: Range = Range.M3,
    val all: List<UfValue> = emptyList(),
    val scrubIndex: Int? = null,
    val lookupDate: LocalDate = LocalDate.now(),
    val lookup: LookupResult = LookupResult.Loading,
    val loadingOlder: Boolean = false,
    val historyMessage: String? = null,
) {
    val dailyDelta: BigDecimal?
        get() = if (current != null && previous != null)
            UfEngine.delta(previous.value, current.value) else null

    val monthDeltaPct: BigDecimal?
        get() = if (current != null && monthAgo != null)
            UfEngine.deltaPct(monthAgo.value, current.value) else null

    /** Collapsed shows a fixed recent window; expanded honours [range]. */
    val chartValues: List<UfValue>
        get() = if (!historyOpen) {
            all.filter { !it.date.isAfter(LocalDate.now()) }.takeLast(60)
        } else {
            val months = range.months ?: return all
            val from = LocalDate.now().minusMonths(months)
            all.filter { !it.date.isBefore(from) }
        }

    /** Last day with a published value; the newest date a lookup may ask about. */
    val lastPublished: LocalDate?
        get() = UfLookup.maxSelectable(all)

    /** The day the finger is on, if any. Never replaces [current] in the UI. */
    val scrubbed: UfValue?
        get() = scrubIndex?.let { chartValues.getOrNull(it) }

    val rangeChangePct: BigDecimal?
        get() {
            val v = chartValues
            val first = v.firstOrNull() ?: return null
            val last = v.lastOrNull() ?: return null
            return UfEngine.deltaPct(first.value, last.value)
        }

    /** The same movement expressed as a constant annual rate. */
    val rangeAnnualisedPct: BigDecimal?
        get() {
            val v = chartValues
            val first = v.firstOrNull() ?: return null
            val last = v.lastOrNull() ?: return null
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
        refresh()
    }

    private fun project(series: List<UfValue>, indicators: List<Indicator>, sync: SyncInfo) {
        val today = LocalDate.now()
        val current = UfEngine.currentOf(series, today)
        val previous = current?.let { c -> series.filter { it.date < c.date }.maxByOrNull { it.date } }
        val monthAgo = current?.let { c ->
            val target = c.date.minusMonths(1)
            series.filter { !it.date.isAfter(target) }.maxByOrNull { it.date }
        }
        _ui.value = _ui.value.copy(
            loading = false,
            current = current,
            previous = previous,
            monthAgo = monthAgo,
            future = UfEngine.futureOf(series, today),
            all = series,
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

    fun refresh() {
        if (_ui.value.refreshing) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(refreshing = true, error = null)
            val result = repo.sync()
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

    fun toggleHistory() {
        val opening = !_ui.value.historyOpen
        _ui.value = _ui.value.copy(historyOpen = opening, scrubIndex = null)
        if (opening) {
            ensureFrom(LocalDate.now().minusMonths(_ui.value.range.months ?: 12).year)
            if (_ui.value.lookup == LookupResult.Loading) lookup(_ui.value.lookupDate)
        }
    }

    fun setRange(range: Range) {
        _ui.value = _ui.value.copy(range = range, scrubIndex = null)
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
