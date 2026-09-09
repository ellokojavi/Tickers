package cl.tickers.app.ui.inflation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.data.repo.UfRepository
import cl.tickers.app.domain.engine.LookupResult
import cl.tickers.app.domain.engine.UfLookup
import cl.tickers.app.domain.engine.UfReajuste
import cl.tickers.app.domain.model.ReajusteResult
import cl.tickers.app.domain.model.UfValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class InflationUiState(
    val ready: Boolean = false,
    val amountText: String = "4000",
    val from: LocalDate = LocalDate.of(1990, 1, 1),
    val to: LocalDate = LocalDate.now(),
    val earliest: LocalDate = UfLookup.SERIES_START,
    val latest: LocalDate = LocalDate.now(),
    val result: ReajusteResult? = null,
    /** Set when a date resolved to a neighbouring day rather than itself. */
    val substituted: LocalDate? = null,
    val error: String? = null,
)

/**
 * Restates an amount between two dates.
 *
 * Dates, not months: the UF is published every day, so the conversion is exact
 * at day precision. The screen used to ask for months and show a second,
 * parallel monthly reading beside the daily one, because the CPI only exists
 * as a monthly statistic and the two answered subtly different questions.
 * Choosing days makes the question well posed and leaves a single answer.
 */
class InflationViewModel(private val repo: UfRepository) : ViewModel() {

    private val _ui = MutableStateFlow(InflationUiState())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            val latest = repo.lastPublishedDate() ?: LocalDate.now()
            _ui.value = _ui.value.copy(
                ready = true,
                latest = latest,
                to = _ui.value.to.coerceAtMost(latest),
            )
            compute()
        }
    }

    fun setAmount(text: String) {
        _ui.value = _ui.value.copy(amountText = text)
        compute()
    }

    fun setFrom(date: LocalDate) {
        _ui.value = _ui.value.copy(from = date)
        compute()
    }

    fun setTo(date: LocalDate) {
        _ui.value = _ui.value.copy(to = date)
        compute()
    }

    fun swap() {
        val s = _ui.value
        _ui.value = s.copy(from = s.to, to = s.from)
        compute()
    }

    private fun compute() {
        viewModelScope.launch {
            val s = _ui.value
            val amount = Fmt.parseNumber(s.amountText)
            if (amount == null || amount.signum() <= 0) {
                _ui.value = s.copy(result = null, substituted = null, error = null)
                return@launch
            }

            val fromUf = resolve(s.from)
            val toUf = resolve(s.to)
            if (fromUf == null || toUf == null) {
                _ui.value = s.copy(
                    result = null,
                    substituted = null,
                    error = "Sin valor de la UF para esa fecha. La serie va del " +
                        "${Fmt.shortDate(s.earliest)} al ${Fmt.shortDate(s.latest)}.",
                )
                return@launch
            }

            _ui.value = s.copy(
                result = UfReajuste.convert(amount, fromUf, toUf),
                // A date that resolved to a neighbour is surfaced rather than
                // passed off as exact.
                substituted = listOf(fromUf to s.from, toUf to s.to)
                    .firstOrNull { (value, asked) -> value.date != asked }?.second,
                error = null,
            )
        }
    }

    private suspend fun resolve(date: LocalDate): UfValue? {
        val outcome = UfLookup.resolve(
            requested = date,
            exact = repo.exactUfOn(date),
            nearestEarlier = repo.nearestUfOn(date),
            lastPublished = _ui.value.latest,
            today = LocalDate.now(),
        )
        return when (outcome) {
            is LookupResult.Exact -> outcome.value
            is LookupResult.Nearest -> outcome.value
            else -> null
        }
    }
}
