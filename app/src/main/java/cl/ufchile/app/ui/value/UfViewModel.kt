package cl.ufchile.app.ui.value

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.data.prefs.SettingsStore
import cl.ufchile.app.data.prefs.SyncInfo
import cl.ufchile.app.data.repo.UfRepository
import cl.ufchile.app.domain.engine.UfEngine
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
    val lookupValue: UfValue? = null,
    val lookupIsFuture: Boolean = false,
    val lookupMissing: Boolean = false,
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
            clpText = _ui.value.clpText.ifBlank {
                current?.let { Fmt.clpExact(it.value).removePrefix("$") }.orEmpty()
            },
            lookupValue = _ui.value.lookupValue
                ?: series.firstOrNull { it.date == _ui.value.lookupDate },
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
                Fmt.clpExact(UfEngine.ufToClp(uf, rate)).removePrefix("$") else "",
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
            if (_ui.value.lookupValue == null) lookup(_ui.value.lookupDate)
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
            val exact = _ui.value.all.firstOrNull { it.date == date }
            if (exact == null && _ui.value.all.none { it.date.year == date.year }) {
                ensureFrom(date.year)
            }
            val value = exact ?: repo.ufOn(date)
            _ui.value = _ui.value.copy(
                lookupDate = date,
                lookupValue = value,
                lookupIsFuture = value != null && value.date.isAfter(LocalDate.now()),
                lookupMissing = value == null || value.date != date,
            )
        }
    }

    /** Downloads any year in [year]..now that the cache does not hold yet. */
    private fun ensureFrom(year: Int) {
        val missing = (year..LocalDate.now().year)
            .filter { y -> _ui.value.all.none { it.date.year == y } }
        if (missing.isEmpty()) return
        viewModelScope.launch {
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
}
