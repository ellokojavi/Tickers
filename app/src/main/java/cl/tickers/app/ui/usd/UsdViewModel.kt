package cl.tickers.app.ui.usd

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.data.seed.UfDailySeed
import cl.tickers.app.domain.engine.FxEngine
import cl.tickers.app.domain.engine.UfEngine
import cl.tickers.app.domain.model.UfValue
import cl.tickers.app.ui.value.Range
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The dólar observado, read from the bundled series.
 *
 * No database behind it, unlike the UF. The UF needs one because the app writes
 * newly published values back as they arrive; the dollar series is regenerated
 * daily into the asset itself, so there is nothing to reconcile and a table
 * would be a copy of a file.
 */
data class UsdUiState(
    val loading: Boolean = true,
    val series: List<UfValue> = emptyList(),
    val current: UfValue? = null,
    val previous: UfValue? = null,
    val range: Range = Range.Y1,
    val rangeValues: List<UfValue> = emptyList(),
    val chartValues: List<UfValue> = emptyList(),
    val scrubIndex: Int? = null,
    val usdText: String = "1",
    val clpText: String = "",
) {
    /** The line above the chart, read off the full window rather than the thinned one. */
    val chartSummary: UfEngine.ChartSummary?
        get() {
            val first = rangeValues.firstOrNull() ?: return null
            val last = rangeValues.lastOrNull() ?: return null
            return UfEngine.chartSummary(
                first.value,
                last.value,
                ChronoUnit.DAYS.between(first.date, last.date),
            )
        }

    val dailyDelta: BigDecimal?
        get() = if (current != null && previous != null) {
            UfEngine.delta(previous.value, current.value)
        } else {
            null
        }
}

class UsdViewModel(private val context: Context) : ViewModel() {

    private val _ui = MutableStateFlow(UsdUiState())
    val ui: StateFlow<UsdUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val series = withContext(Dispatchers.IO) {
                UfDailySeed.readAll(context, UfDailySeed.USD_ASSET)
            }
            val today = LocalDate.now()
            val current = UfEngine.currentOf(series, today)
            val previous = current?.let { c ->
                series.filter { it.date < c.date }.maxByOrNull { it.date }
            }
            _ui.update {
                it.copy(
                    loading = false,
                    series = series,
                    current = current,
                    previous = previous,
                    clpText = current?.let { c ->
                        FxEngine.usdToClp(BigDecimal.ONE, c.value).toPlainString()
                    }.orEmpty(),
                )
            }
            applyRange(_ui.value.range)
        }
    }

    fun onRange(range: Range) {
        _ui.update { it.copy(range = range, scrubIndex = null) }
        applyRange(range)
    }

    private fun applyRange(range: Range) {
        val windowed = UfEngine.historyWindow(_ui.value.series, range.months)
        _ui.update {
            it.copy(
                rangeValues = windowed,
                chartValues = UfEngine.downsample(windowed, MAX_CHART_POINTS),
            )
        }
    }

    fun onScrub(index: Int?) = _ui.update { it.copy(scrubIndex = index) }

    fun onUsdInput(text: String) {
        val amount = Fmt.parseNumber(text)
        val rate = _ui.value.current?.value
        _ui.update {
            it.copy(
                usdText = text,
                clpText = if (amount != null && rate != null) {
                    FxEngine.usdToClp(amount, rate).toPlainString()
                } else {
                    ""
                },
            )
        }
    }

    fun onClpInput(text: String) {
        val amount = Fmt.parseNumber(text)
        val rate = _ui.value.current?.value
        _ui.update {
            it.copy(
                clpText = text,
                usdText = if (amount != null && rate != null) {
                    FxEngine.clpToUsd(amount, rate).toPlainString().replace('.', ',')
                } else {
                    ""
                },
            )
        }
    }

    private companion object {
        const val MAX_CHART_POINTS = 400
    }
}
