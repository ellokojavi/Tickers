package cl.ufchile.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.ufchile.app.data.repo.UfRepository
import cl.ufchile.app.domain.engine.UfEngine
import cl.ufchile.app.domain.model.UfValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

enum class Range(val label: String, val months: Long?) {
    M1("1M", 1), M3("3M", 3), M6("6M", 6),
    Y1("1A", 12), Y5("5A", 60), MAX("Máx", null),
}

data class HistoryUiState(
    val loading: Boolean = true,
    val range: Range = Range.Y1,
    val all: List<UfValue> = emptyList(),
    val visible: List<UfValue> = emptyList(),
    val scrubIndex: Int? = null,
    /** Date the user picked in the lookup card. */
    val lookupDate: LocalDate = LocalDate.now(),
    val lookupValue: UfValue? = null,
    val lookupIsFuture: Boolean = false,
    val lookupMissing: Boolean = false,
    val loadingOlder: Boolean = false,
    val message: String? = null,
) {
    val first: UfValue? get() = visible.firstOrNull()
    val last: UfValue? get() = visible.lastOrNull()

    val changePct: BigDecimal?
        get() = if (first != null && last != null)
            UfEngine.deltaPct(first!!.value, last!!.value) else null

    val selected: UfValue?
        get() = scrubIndex?.let { visible.getOrNull(it) }
}

class HistoryViewModel(private val repo: UfRepository) : ViewModel() {

    private val _ui = MutableStateFlow(HistoryUiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeSeries().collect { series ->
                _ui.value = _ui.value.copy(
                    loading = false,
                    all = series,
                    visible = clip(series, _ui.value.range),
                )
                if (_ui.value.lookupValue == null) lookup(_ui.value.lookupDate)
            }
        }
    }

    fun setRange(range: Range) {
        _ui.value = _ui.value.copy(range = range, visible = clip(_ui.value.all, range), scrubIndex = null)
        // Long ranges need years the cache may not hold yet.
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
            if (exact == null && date.year !in _ui.value.all.map { it.date.year }.toSet()) {
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

    private fun ensureFrom(year: Int) {
        val years = (year..LocalDate.now().year).toList()
        val missing = years.filter { y -> _ui.value.all.none { it.date.year == y } }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loadingOlder = true, message = null)
            var failed = false
            missing.forEach { y -> if (repo.ensureYear(y).isFailure) failed = true }
            _ui.value = _ui.value.copy(
                loadingOlder = false,
                message = if (failed) "Faltan años que no se pudieron descargar." else null,
            )
        }
    }

    private fun clip(series: List<UfValue>, range: Range): List<UfValue> {
        val months = range.months ?: return series
        val from = LocalDate.now().minusMonths(months)
        return series.filter { !it.date.isBefore(from) }
    }
}
