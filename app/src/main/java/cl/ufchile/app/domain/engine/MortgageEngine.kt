package cl.ufchile.app.domain.engine

import cl.ufchile.app.domain.model.AmortizationRow
import cl.ufchile.app.domain.model.MortgageInput
import cl.ufchile.app.domain.model.MortgageResult
import cl.ufchile.app.domain.model.PrepaymentMode
import cl.ufchile.app.domain.model.RateConvention
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Builds the amortisation schedule of a UF-denominated mortgage (UF + x%).
 *
 * Uses the French system: a constant capital-plus-interest payment. Insurance
 * is charged on top of that payment and is therefore *not* constant, since the
 * desgravamen premium follows the outstanding balance.
 *
 * All money is BigDecimal in UF at [MONEY_SCALE] decimals. Nothing here uses
 * Double for money; doubles appear only where a rate is being solved for.
 */
object MortgageEngine {

    /** UF amounts are quoted with four decimals in Chilean payment tables. */
    const val MONEY_SCALE = 4
    private const val RATE_SCALE = 12
    private val MC = MathContext.DECIMAL64
    private val HUNDRED = BigDecimal(100)

    fun monthlyRate(annualRatePct: BigDecimal, convention: RateConvention): BigDecimal {
        val annual = annualRatePct.divide(HUNDRED, RATE_SCALE, RoundingMode.HALF_UP)
        return when (convention) {
            RateConvention.NOMINAL_DIVIDED ->
                annual.divide(BigDecimal(12), RATE_SCALE, RoundingMode.HALF_UP)

            RateConvention.EFFECTIVE_EQUIVALENT -> {
                // The twelfth root has no exact BigDecimal form; solving it in
                // double is fine because this is a rate, not an amount.
                val m = Math.pow(1.0 + annual.toDouble(), 1.0 / 12.0) - 1.0
                BigDecimal(m).setScale(RATE_SCALE, RoundingMode.HALF_UP)
            }
        }
    }

    /** Constant French payment for [principal] at [i] per month over [n] months. */
    fun payment(principal: BigDecimal, i: BigDecimal, n: Int): BigDecimal {
        if (n <= 0) return BigDecimal.ZERO.setScale(MONEY_SCALE)
        if (i.signum() == 0) {
            return principal.divide(BigDecimal(n), MONEY_SCALE, RoundingMode.HALF_UP)
        }
        val onePlusI = BigDecimal.ONE + i
        val discount = BigDecimal.ONE.divide(onePlusI.pow(n, MC), MC) // (1+i)^-n
        return principal.multiply(i)
            .divide(BigDecimal.ONE - discount, MONEY_SCALE, RoundingMode.HALF_UP)
    }

    fun simulate(input: MortgageInput): MortgageResult {
        val i = monthlyRate(input.annualRatePct, input.rateConvention)
        val principal = input.loanAmountUf.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        val n = input.termMonths
        val basePayment = payment(principal, i, n)

        val lifeRate = input.lifeInsuranceMonthlyPct.divide(HUNDRED, RATE_SCALE, RoundingMode.HALF_UP)
        val fire = input.fireInsuranceMonthlyUf.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        val prepaysByMonth = input.prepayments.groupBy { it.monthNumber }

        val rows = ArrayList<AmortizationRow>(n)
        var balance = principal
        var currentPayment = basePayment
        var month = 1
        // A prepayment can only ever shorten the schedule, so n is a hard cap.
        while (balance.signum() > 0 && month <= n) {
            val opening = balance
            var interest = opening.multiply(i).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            var principalPart = (currentPayment - interest).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            var thisPayment = currentPayment

            // Final instalment: settle whatever is left rather than overshoot.
            if (principalPart >= opening) {
                principalPart = opening
                thisPayment = (principalPart + interest).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            }
            // A negative-amortising payment would never repay the loan.
            if (principalPart.signum() < 0) principalPart = BigDecimal.ZERO.setScale(MONEY_SCALE)

            val life = opening.multiply(lifeRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            var afterPrincipal = (opening - principalPart).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

            var prepaid = BigDecimal.ZERO.setScale(MONEY_SCALE)
            val prepays = prepaysByMonth[month]
            if (prepays != null && afterPrincipal.signum() > 0) {
                for (p in prepays) {
                    val applied = p.amountUf.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                        .min(afterPrincipal)
                    if (applied.signum() <= 0) continue
                    prepaid = prepaid + applied
                    afterPrincipal = afterPrincipal - applied
                    if (p.mode == PrepaymentMode.REDUCE_PAYMENT) {
                        val remaining = n - month
                        currentPayment =
                            if (remaining > 0) payment(afterPrincipal, i, remaining)
                            else afterPrincipal
                    }
                    // REDUCE_TERM keeps the payment; the loop simply ends sooner.
                }
            }

            val closing = afterPrincipal
            val outflow = (thisPayment + life + fire + prepaid)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP)

            rows.add(
                AmortizationRow(
                    number = month,
                    date = input.startDate.plusMonths(month.toLong()),
                    openingBalanceUf = opening,
                    interestUf = interest,
                    principalUf = principalPart,
                    paymentUf = thisPayment,
                    lifeInsuranceUf = life,
                    fireInsuranceUf = fire,
                    prepaymentUf = prepaid,
                    totalOutflowUf = outflow,
                    closingBalanceUf = closing,
                )
            )
            balance = closing
            month++
        }

        val stampTax = principal.multiply(input.stampTaxPct)
            .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)
        val upfront = (input.originationFeeUf + stampTax + input.otherUpfrontCostsUf)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)

        val totalInterest = rows.sumUf { it.interestUf }
        val totalLife = rows.sumUf { it.lifeInsuranceUf }
        val totalFire = rows.sumUf { it.fireInsuranceUf }
        val totalPrepay = rows.sumUf { it.prepaymentUf }
        val totalOutflow = rows.sumUf { it.totalOutflowUf }

        return MortgageResult(
            input = input,
            loanAmountUf = principal,
            monthlyRate = i,
            basePaymentUf = basePayment,
            firstTotalPaymentUf = rows.firstOrNull()?.totalOutflowUf
                ?: BigDecimal.ZERO.setScale(MONEY_SCALE),
            schedule = rows,
            totalInterestUf = totalInterest,
            totalLifeInsuranceUf = totalLife,
            totalFireInsuranceUf = totalFire,
            totalPrepaymentsUf = totalPrepay,
            upfrontCostsUf = upfront,
            totalCostUf = (totalOutflow + upfront).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            caePct = solveCae(principal, upfront, rows.map { it.totalOutflowUf }),
            effectiveTermMonths = rows.size,
            monthsSaved = (n - rows.size).coerceAtLeast(0),
        )
    }

    /**
     * Carga Anual Equivalente: the annual rate that makes the present value of
     * everything the borrower pays equal the net amount actually received.
     *
     * Solved by bisection on the monthly rate. Returns null when the cash flows
     * have no sign change and therefore no root.
     */
    fun solveCae(
        principal: BigDecimal,
        upfrontCosts: BigDecimal,
        outflows: List<BigDecimal>,
    ): BigDecimal? {
        if (outflows.isEmpty() || principal.signum() <= 0) return null
        val net = (principal - upfrontCosts).toDouble()
        if (net <= 0.0) return null
        val flows = outflows.map { it.toDouble() }

        fun npv(monthly: Double): Double {
            var acc = net
            var discount = 1.0
            val step = 1.0 + monthly
            for (f in flows) {
                discount *= step
                acc -= f / discount
            }
            return acc
        }

        var lo = -0.9999
        var hi = 5.0
        if (npv(lo) * npv(hi) > 0) return null
        repeat(200) {
            val mid = (lo + hi) / 2
            if (npv(lo) * npv(mid) <= 0) hi = mid else lo = mid
        }
        val monthly = (lo + hi) / 2
        val annual = (Math.pow(1.0 + monthly, 12.0) - 1.0) * 100.0
        if (annual.isNaN() || annual.isInfinite()) return null
        return BigDecimal(annual).setScale(2, RoundingMode.HALF_UP)
    }

    private inline fun List<AmortizationRow>.sumUf(sel: (AmortizationRow) -> BigDecimal): BigDecimal =
        fold(BigDecimal.ZERO) { acc, r -> acc + sel(r) }
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
}
