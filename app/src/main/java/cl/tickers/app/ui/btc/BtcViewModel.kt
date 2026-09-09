package cl.tickers.app.ui.btc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.tickers.app.data.remote.BtcCandle
import cl.tickers.app.data.remote.BtcRemoteDataSource
import cl.tickers.app.data.remote.BtcSpotResult
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.data.remote.Connectivity
import cl.tickers.app.domain.engine.ConverterEngine
import cl.tickers.app.domain.engine.FetchErrorKind
import cl.tickers.app.domain.engine.Horizon
import cl.tickers.app.domain.engine.chartPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class BtcUiState(
    val usd: BigDecimal? = null,
    val source: String = "",
    val fetchedAt: Long = 0L,
    val error: FetchErrorKind? = null,
    val horizon: Horizon = Horizon.D30,
    val candles: List<BtcCandle> = emptyList(),
    val chartLoading: Boolean = true,
    val scrubIndex: Int? = null,
    val btcText: String = "1",
    val usdText: String = "",
    val clpText: String = "",
) {
    /** The price under the finger while scrubbing, otherwise the live one. */
    val shown: BigDecimal? get() = scrubIndex?.let { candles.getOrNull(it)?.usd } ?: usd
}

class BtcViewModel(
    private val context: Context,
    private val source: BtcRemoteDataSource,
) : ViewModel() {

    private val _ui = MutableStateFlow(BtcUiState())
    val ui: StateFlow<BtcUiState> = _ui.asStateFlow()

    private var chartJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                refreshSpot()
                delay(SPOT_INTERVAL_MS)
            }
        }
        loadChart(Horizon.D30)
    }

    fun refreshSpot() {
        viewModelScope.launch {
            when (val result = source.spot(Connectivity.status(context))) {
                is BtcSpotResult.Ok -> _ui.update {
                    it.copy(
                        usd = result.usd,
                        source = result.source,
                        fetchedAt = result.at,
                        error = null,
                    )
                }
                is BtcSpotResult.Failed -> _ui.update { it.copy(error = result.kind) }
            }
        }
    }

    fun onHorizon(h: Horizon) {
        if (_ui.value.horizon == h) return
        _ui.update {
            it.copy(horizon = h, candles = emptyList(), chartLoading = true, scrubIndex = null)
        }
        loadChart(h)
    }

    /**
     * One chart job at a time. Cancelling the previous one is what stops a slow
     * answer for a span the user has already moved off from being drawn over
     * the one they are looking at.
     */
    private fun loadChart(h: Horizon) {
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            while (isActive) {
                runCatching { source.candles(h) }
                    .onSuccess { points ->
                        _ui.update {
                            if (it.horizon == h) {
                                it.copy(candles = points, chartLoading = false)
                            } else {
                                it
                            }
                        }
                    }
                    .onFailure {
                        _ui.update { if (it.horizon == h) it.copy(chartLoading = false) else it }
                    }
                delay(chartPlan(h).refreshMs)
            }
        }
    }

    fun onScrub(index: Int?) = _ui.update { it.copy(scrubIndex = index) }

    fun onBtcInput(raw: String, price: BigDecimal, rate: BigDecimal) =
        onConverterInput(ConverterEngine.BtcField.BTC, raw, price, rate)

    fun onUsdInput(raw: String, price: BigDecimal, rate: BigDecimal) =
        onConverterInput(ConverterEngine.BtcField.USD, raw, price, rate)

    fun onClpInput(raw: String, price: BigDecimal, rate: BigDecimal) =
        onConverterInput(ConverterEngine.BtcField.CLP, raw, price, rate)

    /**
     * One value is typed, the other two follow. The arithmetic lives in the
     * domain because it has to give the same answers here and on the web, and
     * because deriving one rounded figure from another is how a converter
     * starts disagreeing with itself.
     */
    private fun onConverterInput(
        field: ConverterEngine.BtcField,
        raw: String,
        price: BigDecimal,
        rate: BigDecimal,
    ) {
        val amount = Fmt.parseNumber(raw)
        val result = amount?.let { ConverterEngine.btcConvert(field, it, price, rate) }
        _ui.update {
            it.copy(
                btcText = if (field == ConverterEngine.BtcField.BTC) raw
                else result?.btc?.toPlainString()?.replace('.', ',').orEmpty(),
                usdText = if (field == ConverterEngine.BtcField.USD) raw
                else result?.usd?.toPlainString()?.replace('.', ',').orEmpty(),
                clpText = if (field == ConverterEngine.BtcField.CLP) raw
                else result?.clp?.toPlainString().orEmpty(),
            )
        }
    }

    private companion object {
        const val SPOT_INTERVAL_MS = 30_000L
    }
}
