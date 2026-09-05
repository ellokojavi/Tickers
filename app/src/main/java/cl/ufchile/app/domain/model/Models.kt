package cl.ufchile.app.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** A single published UF value for a calendar day. */
data class UfValue(
    val date: LocalDate,
    val value: BigDecimal,
) {
    /** True when this value is published for a date that has not arrived yet. */
    fun isFuture(today: LocalDate = LocalDate.now()): Boolean = date.isAfter(today)
}

/** Where a piece of data came from. Surfaced in the UI so the user can judge it. */
enum class DataSource(val label: String, val official: Boolean) {
    CMF("CMF (oficial)", true),
    MINDICADOR("mindicador.cl", false),
    BUNDLED("Datos incluidos en la app", false),
    CACHE("Caché local", false),
}

/** Non-UF indicators that ride along in the same API response. */
data class Indicator(
    val code: String,
    val name: String,
    val unit: String,
    val date: LocalDate,
    val value: BigDecimal,
)

// ---------------------------------------------------------------- inflation

data class InflationResult(
    val amount: BigDecimal,
    val from: YearMonth,
    val to: YearMonth,
    val adjustedAmount: BigDecimal,
    /** Multiplicative factor, e.g. 7.1567 */
    val factor: BigDecimal,
    /** Cumulative inflation over the period, in percent. */
    val cumulativePct: BigDecimal,
    /** Equivalent constant annual rate, in percent. */
    val annualizedPct: BigDecimal,
    val months: Int,
)

/** The same amount expressed through the UF instead of the CPI index. */
data class UfEquivalence(
    val ufUnits: BigDecimal,
    val ufValueAtOrigin: BigDecimal,
    val ufValueAtTarget: BigDecimal,
    val adjustedAmount: BigDecimal,
)

// ---------------------------------------------------------------- mortgage

/**
 * How an annual rate is turned into a monthly one.
 *
 * Chilean lenders quote an annual rate but differ in the conversion. Both
 * conventions are supported so a simulation can be matched against a specific
 * bank's own quote.
 */
enum class RateConvention(val label: String) {
    /** monthly = annual / 12. The common convention for Chilean mortgages. */
    NOMINAL_DIVIDED("Nominal (anual / 12)"),

    /** monthly = (1 + annual)^(1/12) - 1. */
    EFFECTIVE_EQUIVALENT("Efectiva equivalente"),
}

enum class PrepaymentMode(val label: String) {
    REDUCE_TERM("Acortar plazo"),
    REDUCE_PAYMENT("Bajar la cuota"),
}

data class Prepayment(
    val monthNumber: Int,
    val amountUf: BigDecimal,
    val mode: PrepaymentMode,
)

/**
 * All inputs of a mortgage simulation. Amounts are in UF because the debt
 * itself is denominated in UF; pesos are a presentation concern.
 */
data class MortgageInput(
    val propertyValueUf: BigDecimal,
    val downPaymentUf: BigDecimal,
    val annualRatePct: BigDecimal,
    val termYears: Int,
    val rateConvention: RateConvention = RateConvention.NOMINAL_DIVIDED,
    /** Desgravamen: monthly percentage applied to the outstanding balance. */
    val lifeInsuranceMonthlyPct: BigDecimal = BigDecimal.ZERO,
    /** Incendio y sismo: fixed UF amount charged every month. */
    val fireInsuranceMonthlyUf: BigDecimal = BigDecimal.ZERO,
    /** Comisión de originación, charged once up front. */
    val originationFeeUf: BigDecimal = BigDecimal.ZERO,
    /** Impuesto de timbres y estampillas, percentage of the loan amount. */
    val stampTaxPct: BigDecimal = BigDecimal.ZERO,
    /** Notaría, conservador, tasación, etc. */
    val otherUpfrontCostsUf: BigDecimal = BigDecimal.ZERO,
    val startDate: LocalDate = LocalDate.now(),
    val prepayments: List<Prepayment> = emptyList(),
) {
    val loanAmountUf: BigDecimal get() = (propertyValueUf - downPaymentUf).max(BigDecimal.ZERO)
    val termMonths: Int get() = termYears * 12
    val financedPct: BigDecimal
        get() = if (propertyValueUf.signum() == 0) BigDecimal.ZERO
        else loanAmountUf.divide(propertyValueUf, 6, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
}

data class AmortizationRow(
    val number: Int,
    val date: LocalDate,
    val openingBalanceUf: BigDecimal,
    val interestUf: BigDecimal,
    val principalUf: BigDecimal,
    /** Capital + interest, i.e. the dividend before insurance. */
    val paymentUf: BigDecimal,
    val lifeInsuranceUf: BigDecimal,
    val fireInsuranceUf: BigDecimal,
    val prepaymentUf: BigDecimal,
    /** Everything actually paid this month. */
    val totalOutflowUf: BigDecimal,
    val closingBalanceUf: BigDecimal,
)

data class MortgageResult(
    val input: MortgageInput,
    val loanAmountUf: BigDecimal,
    val monthlyRate: BigDecimal,
    /** Capital + interest only. Constant for a French schedule. */
    val basePaymentUf: BigDecimal,
    /** First month's total outflow, including insurance. */
    val firstTotalPaymentUf: BigDecimal,
    val schedule: List<AmortizationRow>,
    val totalInterestUf: BigDecimal,
    val totalLifeInsuranceUf: BigDecimal,
    val totalFireInsuranceUf: BigDecimal,
    val totalPrepaymentsUf: BigDecimal,
    val upfrontCostsUf: BigDecimal,
    /** Everything the borrower pays over the life of the loan. */
    val totalCostUf: BigDecimal,
    /** Carga Anual Equivalente, in percent. Null when it cannot be solved. */
    val caePct: BigDecimal?,
    val effectiveTermMonths: Int,
    /** Months saved versus the nominal term, when prepayments shorten it. */
    val monthsSaved: Int,
)

/** A saved, named simulation. */
data class Simulation(
    val id: Long = 0L,
    val name: String,
    val notes: String = "",
    val input: MortgageInput,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
