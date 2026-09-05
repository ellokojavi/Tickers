package cl.ufchile.app.ui.inflation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.data.repo.UfRepository
import cl.ufchile.app.domain.engine.InflationEngine
import cl.ufchile.app.domain.model.InflationResult
import cl.ufchile.app.domain.model.UfEquivalence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth

data class InflationUiState(
    val ready: Boolean = false,
    val amountText: String = "4.000",
    val from: YearMonth = YearMonth.of(1990, 1),
    val to: YearMonth = YearMonth.now().minusMonths(1),
    val earliest: YearMonth = InflationEngine.EARLIEST,
    val latest: YearMonth = YearMonth.now(),
    val result: InflationResult? = null,
    val ufEquivalence: UfEquivalence? = null,
    val error: String? = null,
)

class InflationViewModel(private val repo: UfRepository) : ViewModel() {

    private val _ui = MutableStateFlow(InflationUiState())
    val ui = _ui.asStateFlow()

    private var engine: InflationEngine? = null
    private var anchors: Map<YearMonth, BigDecimal> = emptyMap()

    init {
        viewModelScope.launch {
            anchors = repo.anchors()
            val e = InflationEngine(anchors)
            engine = e
            _ui.value = _ui.value.copy(
                ready = true,
                earliest = e.earliestMonth,
                latest = e.latestMonth,
                to = e.latestMonth,
            )
            compute()
        }
    }

    fun setAmount(text: String) {
        _ui.value = _ui.value.copy(amountText = text)
        compute()
    }

    fun setFrom(month: YearMonth) {
        _ui.value = _ui.value.copy(from = month)
        compute()
    }

    fun setTo(month: YearMonth) {
        _ui.value = _ui.value.copy(to = month)
        compute()
    }

    fun swap() {
        val s = _ui.value
        _ui.value = s.copy(from = s.to, to = s.from)
        compute()
    }

    private fun compute() {
        val e = engine ?: return
        val s = _ui.value
        val amount = Fmt.parseNumber(s.amountText)
        if (amount == null || amount.signum() <= 0) {
            _ui.value = s.copy(result = null, ufEquivalence = null, error = null)
            return
        }
        try {
            val result = e.convert(amount, s.from, s.to)
            // The UF reading uses the UF value inside each month itself, which
            // is a different (and independent) way of restating the amount.
            val ufFrom = anchors[s.from]
            val ufTo = anchors[s.to]
            _ui.value = s.copy(
                result = result,
                ufEquivalence = if (ufFrom != null && ufTo != null)
                    e.asUfEquivalence(amount, ufFrom, ufTo) else null,
                error = null,
            )
        } catch (t: IllegalArgumentException) {
            _ui.value = s.copy(result = null, ufEquivalence = null, error = t.message)
        }
    }
}
