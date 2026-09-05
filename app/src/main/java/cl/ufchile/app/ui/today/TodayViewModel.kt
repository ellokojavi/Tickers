package cl.ufchile.app.ui.today

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

data class TodayUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val current: UfValue? = null,
    val previous: UfValue? = null,
    val monthAgo: UfValue? = null,
    val future: List<UfValue> = emptyList(),
    val recent: List<UfValue> = emptyList(),
    val indicators: List<Indicator> = emptyList(),
    val sync: SyncInfo = SyncInfo(null, 0L),
    val error: String? = null,
    val ufText: String = "1",
    val clpText: String = "",
) {
    val dailyDelta: BigDecimal?
        get() = if (current != null && previous != null)
            UfEngine.delta(previous.value, current.value) else null

    val monthDeltaPct: BigDecimal?
        get() = if (current != null && monthAgo != null)
            UfEngine.deltaPct(monthAgo.value, current.value) else null
}

class TodayViewModel(
    private val repo: UfRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(TodayUiState())
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
        val previous = current?.let { c ->
            series.filter { it.date < c.date }.maxByOrNull { it.date }
        }
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
            recent = series.filter { !it.date.isAfter(today) }.takeLast(60),
            indicators = indicators,
            sync = sync,
            clpText = _ui.value.clpText.ifBlank {
                current?.let { Fmt.clpExact(it.value).removePrefix("$") }.orEmpty()
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
}
