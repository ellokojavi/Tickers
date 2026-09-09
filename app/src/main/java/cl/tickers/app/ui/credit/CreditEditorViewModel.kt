package cl.tickers.app.ui.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.data.repo.SimulationRepository
import cl.tickers.app.data.repo.UfRepository
import cl.tickers.app.domain.engine.MortgageEngine
import cl.tickers.app.domain.model.MortgageInput
import cl.tickers.app.domain.model.MortgageResult
import cl.tickers.app.domain.model.Prepayment
import cl.tickers.app.domain.model.PrepaymentMode
import cl.tickers.app.domain.model.RateConvention
import cl.tickers.app.domain.model.Simulation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Editable form state. Fields are kept as raw text so partially-typed values
 * ("4," or "") do not fight the user, and are parsed on every recomputation.
 */
data class CreditForm(
    val name: String = "Mi simulación",
    val notes: String = "",
    val propertyValueUf: String = "5000",
    val downPaymentUf: String = "1000",
    val annualRatePct: String = "4,5",
    val termYears: String = "25",
    val rateConvention: RateConvention = RateConvention.NOMINAL_DIVIDED,
    val lifeInsuranceMonthlyPct: String = "0,03",
    val fireInsuranceMonthlyUf: String = "0,4",
    val originationFeeUf: String = "0",
    val stampTaxPct: String = "0,8",
    val otherUpfrontCostsUf: String = "30",
    val firstPaymentDate: LocalDate = LocalDate.now().plusMonths(1),
    val prepayments: List<Prepayment> = emptyList(),
)

data class CreditEditorUiState(
    val id: Long = 0L,
    val loading: Boolean = true,
    val form: CreditForm = CreditForm(),
    val result: MortgageResult? = null,
    val ufValue: BigDecimal? = null,
    val validationError: String? = null,
    val saved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isNew: Boolean get() = id == 0L
}

class CreditEditorViewModel(
    private val simulations: SimulationRepository,
    private val uf: UfRepository,
    private val simulationId: Long,
) : ViewModel() {

    private val _ui = MutableStateFlow(CreditEditorUiState(id = simulationId))
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val ufValue = uf.ufOn(LocalDate.now())?.value
            val existing = if (simulationId != 0L) simulations.byId(simulationId) else null
            _ui.value = _ui.value.copy(
                loading = false,
                ufValue = ufValue,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                form = existing?.let { it.toForm() } ?: CreditForm(),
            )
            recompute()
        }
    }

    fun update(transform: (CreditForm) -> CreditForm) {
        _ui.value = _ui.value.copy(form = transform(_ui.value.form), saved = false)
        recompute()
    }

    fun addPrepayment(month: Int, amountUf: BigDecimal, mode: PrepaymentMode) {
        update { it.copy(prepayments = it.prepayments + Prepayment(month, amountUf, mode)) }
    }

    fun removePrepayment(index: Int) {
        update { it.copy(prepayments = it.prepayments.filterIndexed { i, _ -> i != index }) }
    }

    fun save(onDone: (Long) -> Unit) {
        val input = buildInput() ?: return
        viewModelScope.launch {
            val state = _ui.value
            val id = if (state.isNew) {
                simulations.create(state.form.name.ifBlank { "Simulación" }, state.form.notes, input)
            } else {
                simulations.update(
                    Simulation(
                        id = state.id,
                        name = state.form.name.ifBlank { "Simulación" },
                        notes = state.form.notes,
                        input = input,
                        createdAt = state.createdAt,
                    )
                )
                state.id
            }
            _ui.value = _ui.value.copy(id = id, saved = true)
            onDone(id)
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _ui.value.id
        if (id == 0L) { onDone(); return }
        viewModelScope.launch {
            simulations.delete(id)
            onDone()
        }
    }

    private fun recompute() {
        val input = buildInput()
        _ui.value = _ui.value.copy(
            result = input?.let { MortgageEngine.simulate(it) },
        )
    }

    /** Returns null (and sets a message) when the form cannot produce a loan. */
    private fun buildInput(): MortgageInput? {
        val f = _ui.value.form
        val property = Fmt.parseNumber(f.propertyValueUf)
        val down = Fmt.parseNumber(f.downPaymentUf) ?: BigDecimal.ZERO
        val rate = Fmt.parseNumber(f.annualRatePct)
        val years = Fmt.parseNumber(f.termYears)?.toInt()

        val error = when {
            property == null || property.signum() <= 0 -> "Ingresa el valor de la propiedad."
            down.signum() < 0 -> "El pie no puede ser negativo."
            down >= property -> "El pie no puede ser igual o mayor que la propiedad."
            rate == null || rate.signum() < 0 -> "Ingresa una tasa válida."
            years == null || years !in 1..40 -> "El plazo debe estar entre 1 y 40 años."
            else -> null
        }
        _ui.value = _ui.value.copy(validationError = error)
        if (error != null) return null

        return MortgageInput(
            propertyValueUf = property!!,
            downPaymentUf = down,
            annualRatePct = rate!!,
            termYears = years!!,
            rateConvention = f.rateConvention,
            lifeInsuranceMonthlyPct = Fmt.parseNumber(f.lifeInsuranceMonthlyPct) ?: BigDecimal.ZERO,
            fireInsuranceMonthlyUf = Fmt.parseNumber(f.fireInsuranceMonthlyUf) ?: BigDecimal.ZERO,
            originationFeeUf = Fmt.parseNumber(f.originationFeeUf) ?: BigDecimal.ZERO,
            stampTaxPct = Fmt.parseNumber(f.stampTaxPct) ?: BigDecimal.ZERO,
            otherUpfrontCostsUf = Fmt.parseNumber(f.otherUpfrontCostsUf) ?: BigDecimal.ZERO,
            firstPaymentDate = f.firstPaymentDate,
            prepayments = f.prepayments,
        )
    }

    private fun Simulation.toForm() = CreditForm(
        name = name,
        notes = notes,
        propertyValueUf = input.propertyValueUf.toPlainString().replace('.', ','),
        downPaymentUf = input.downPaymentUf.toPlainString().replace('.', ','),
        annualRatePct = input.annualRatePct.toPlainString().replace('.', ','),
        termYears = input.termYears.toString(),
        rateConvention = input.rateConvention,
        lifeInsuranceMonthlyPct = input.lifeInsuranceMonthlyPct.toPlainString().replace('.', ','),
        fireInsuranceMonthlyUf = input.fireInsuranceMonthlyUf.toPlainString().replace('.', ','),
        originationFeeUf = input.originationFeeUf.toPlainString().replace('.', ','),
        stampTaxPct = input.stampTaxPct.toPlainString().replace('.', ','),
        otherUpfrontCostsUf = input.otherUpfrontCostsUf.toPlainString().replace('.', ','),
        firstPaymentDate = input.firstPaymentDate,
        prepayments = input.prepayments,
    )
}
