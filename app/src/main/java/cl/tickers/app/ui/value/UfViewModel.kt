package cl.tickers.app.ui.value

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.data.prefs.SettingsStore
import cl.tickers.app.data.prefs.SyncInfo
import cl.tickers.app.data.repo.UfRepository
import cl.tickers.app.domain.engine.LookupResult
import cl.tickers.app.domain.engine.ConverterEngine
import cl.tickers.app.domain.engine.UfEngine
import cl.tickers.app.domain.engine.UfLookup
import cl.tickers.app.domain.model.Indicator
import cl.tickers.app.domain.model.UfValue
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
    val usdText: String = "",

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
    /**
     * The observed dollar, already among the indicators the app fetches. It has
     * its own publication day, which the converter shows, because it is not the
     * UF's day.
     */
    val dollar: Indicator? get() = indicators.firstOrNull { it.code == "dolar" }

    val dailyDelta: BigDecimal?
        get() = if (current != null && previous != null)
            UfEngine.delta(previous.value, current.value) else null

    val monthDeltaPct: BigDecimal?
        get() = if (current != null && monthAgo != null)
            UfEngine.deltaPct(monthAgo.value, current.value) else null

    /** Last day with a published value; the newest date a lookup may ask about. */
    val lastPublished: LocalDate?
        get() = UfLookup.maxSelectable(all)

    /** The line above the chart, read off the full window rather than the thinned one. */
    val chartSummary: UfEngine.ChartSummary?
        get() {
            val first = rangeValues.firstOrNull() ?: return null
            val last = rangeValues.lastOrNull() ?: return null
            return UfEngine.chartSummary(
                first.value,
                last.value,
                java.time.temporal.ChronoUnit.DAYS.between(first.date, last.date),
            )
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
        val windowed = UfEngine.historyWindow(series, _ui.value.range.months)
        // One UF converted, so the card answers before anyone types in it.
        val opening = current?.value?.let {
            ConverterEngine.ufConvert(
                ConverterEngine.UfField.UF,
                java.math.BigDecimal.ONE,
                it,
                indicators.firstOrNull { i -> i.code == "dolar" }?.value,
            )
        }
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
            clpText = _ui.value.clpText.ifBlank { opening?.clp?.toPlainString().orEmpty() },
            usdText = _ui.value.usdText.ifBlank {
                opening?.usd?.toPlainString()?.replace('.', ',').orEmpty()
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

    fun onUfInput(text: String) = onConverterInput(ConverterEngine.UfField.UF, text)

    fun onClpInput(text: String) = onConverterInput(ConverterEngine.UfField.CLP, text)

    fun onUsdInput(text: String) = onConverterInput(ConverterEngine.UfField.USD, text)

    /**
     * One value is typed, the other two follow. The arithmetic lives in the
     * domain because it has to give the same answers here and on the web, and
     * because deriving one rounded figure from another is how a converter
     * starts disagreeing with itself.
     */
    private fun onConverterInput(field: ConverterEngine.UfField, text: String) {
        val state = _ui.value
        val amount = Fmt.parseNumber(text)
        val rate = state.current?.value
        val result = if (amount == null || rate == null) {
            null
        } else {
            ConverterEngine.ufConvert(field, amount, rate, state.dollar?.value)
        }

        _ui.value = state.copy(
            ufText = if (field == ConverterEngine.UfField.UF) text
            else result?.uf?.toPlainString()?.replace('.', ',').orEmpty(),
            clpText = if (field == ConverterEngine.UfField.CLP) text
            else result?.clp?.toPlainString().orEmpty(),
            usdText = if (field == ConverterEngine.UfField.USD) text
            else result?.usd?.toPlainString()?.replace('.', ',').orEmpty(),
        )
    }

    // -------------------------------------------------------------- history

    fun toggleDetail() {
        _ui.value = _ui.value.copy(detailExpanded = !_ui.value.detailExpanded)
    }

    fun setRange(range: Range) {
        val windowed = UfEngine.historyWindow(_ui.value.all, range.months)
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
