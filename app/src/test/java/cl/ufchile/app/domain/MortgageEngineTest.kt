package cl.ufchile.app.domain

import cl.ufchile.app.domain.engine.MortgageEngine
import cl.ufchile.app.domain.model.MortgageInput
import cl.ufchile.app.domain.model.Prepayment
import cl.ufchile.app.domain.model.PrepaymentMode
import cl.ufchile.app.domain.model.RateConvention
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class MortgageEngineTest {

    private fun input(
        property: String = "5000",
        down: String = "1000",
        rate: String = "4.5",
        years: Int = 25,
        life: String = "0",
        fire: String = "0",
        stamp: String = "0",
        origination: String = "0",
        other: String = "0",
        prepayments: List<Prepayment> = emptyList(),
        convention: RateConvention = RateConvention.NOMINAL_DIVIDED,
        firstPayment: LocalDate = LocalDate.of(2026, 10, 5),
    ) = MortgageInput(
        propertyValueUf = BigDecimal(property),
        downPaymentUf = BigDecimal(down),
        annualRatePct = BigDecimal(rate),
        termYears = years,
        rateConvention = convention,
        lifeInsuranceMonthlyPct = BigDecimal(life),
        fireInsuranceMonthlyUf = BigDecimal(fire),
        originationFeeUf = BigDecimal(origination),
        stampTaxPct = BigDecimal(stamp),
        otherUpfrontCostsUf = BigDecimal(other),
        firstPaymentDate = firstPayment,
        prepayments = prepayments,
    )

    /** The BigDecimal implementation must agree with the closed-form formula. */
    @Test
    fun `french payment matches the analytic formula`() {
        val principal = 4000.0
        val i = 0.045 / 12
        val n = 300
        val expected = principal * i / (1 - Math.pow(1 + i, -n.toDouble()))

        val actual = MortgageEngine.payment(BigDecimal("4000"), BigDecimal(i), n)

        assertThat(actual.toDouble()).isWithin(0.001).of(expected)
    }

    @Test
    fun `rate conventions differ as expected`() {
        val nominal = MortgageEngine.monthlyRate(BigDecimal("4.5"), RateConvention.NOMINAL_DIVIDED)
        val effective = MortgageEngine.monthlyRate(BigDecimal("4.5"), RateConvention.EFFECTIVE_EQUIVALENT)

        assertThat(nominal.toDouble()).isWithin(1e-9).of(0.045 / 12)
        assertThat(effective.toDouble()).isWithin(1e-9).of(Math.pow(1.045, 1.0 / 12) - 1)
        // The effective conversion is always the cheaper of the two.
        assertThat(effective).isLessThan(nominal)
    }

    @Test
    fun `schedule fully amortises the loan`() {
        val result = MortgageEngine.simulate(input())

        assertThat(result.schedule).hasSize(300)
        assertThat(result.schedule.last().closingBalanceUf.toDouble()).isWithin(0.0001).of(0.0)
    }

    @Test
    fun `principal payments sum to the loan amount`() {
        val result = MortgageEngine.simulate(input())
        val totalPrincipal = result.schedule
            .fold(BigDecimal.ZERO) { acc, r -> acc + r.principalUf }

        assertThat(totalPrincipal.setScale(2, RoundingMode.HALF_UP))
            .isEqualTo(result.loanAmountUf.setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `balance chain is internally consistent`() {
        val result = MortgageEngine.simulate(input(life = "0.03", fire = "0.4"))

        result.schedule.zipWithNext().forEach { (a, b) ->
            assertThat(b.openingBalanceUf).isEqualTo(a.closingBalanceUf)
        }
        result.schedule.forEach { row ->
            assertThat(row.closingBalanceUf)
                .isEqualTo(row.openingBalanceUf - row.principalUf - row.prepaymentUf)
        }
    }

    @Test
    fun `interest equals balance times monthly rate`() {
        val result = MortgageEngine.simulate(input())
        val i = result.monthlyRate

        result.schedule.take(24).forEach { row ->
            val expected = row.openingBalanceUf.multiply(i).setScale(4, RoundingMode.HALF_UP)
            assertThat(row.interestUf).isEqualTo(expected)
        }
    }

    @Test
    fun `insurance is charged on top of the dividend`() {
        val result = MortgageEngine.simulate(input(life = "0.03", fire = "0.4"))
        val first = result.schedule.first()

        assertThat(first.totalOutflowUf)
            .isEqualTo(first.paymentUf + first.lifeInsuranceUf + first.fireInsuranceUf)
        // Desgravamen follows the balance, so it shrinks over time.
        assertThat(result.schedule.last().lifeInsuranceUf)
            .isLessThan(first.lifeInsuranceUf)
    }

    @Test
    fun `zero rate splits the principal evenly`() {
        val result = MortgageEngine.simulate(input(rate = "0", years = 10))

        assertThat(result.totalInterestUf.toDouble()).isWithin(0.0001).of(0.0)
        assertThat(result.basePaymentUf.toDouble()).isWithin(0.001).of(4000.0 / 120)
    }

    @Test
    fun `prepayment that reduces term shortens the schedule`() {
        val base = MortgageEngine.simulate(input())
        val withPrepay = MortgageEngine.simulate(
            input(
                prepayments = listOf(
                    Prepayment(12, BigDecimal("500"), PrepaymentMode.REDUCE_TERM)
                )
            )
        )

        assertThat(withPrepay.schedule.size).isLessThan(base.schedule.size)
        assertThat(withPrepay.monthsSaved).isGreaterThan(0)
        assertThat(withPrepay.totalInterestUf).isLessThan(base.totalInterestUf)
        assertThat(withPrepay.schedule.last().closingBalanceUf.toDouble())
            .isWithin(0.0001).of(0.0)
    }

    @Test
    fun `prepayment that reduces payment keeps the term`() {
        val base = MortgageEngine.simulate(input())
        val withPrepay = MortgageEngine.simulate(
            input(
                prepayments = listOf(
                    Prepayment(12, BigDecimal("500"), PrepaymentMode.REDUCE_PAYMENT)
                )
            )
        )

        assertThat(withPrepay.schedule.size).isEqualTo(base.schedule.size)
        // The dividend after the abono must be smaller than before it.
        assertThat(withPrepay.schedule[20].paymentUf)
            .isLessThan(withPrepay.schedule[5].paymentUf)
        assertThat(withPrepay.schedule.last().closingBalanceUf.toDouble())
            .isWithin(0.0001).of(0.0)
    }

    @Test
    fun `a prepayment cannot exceed the outstanding balance`() {
        val result = MortgageEngine.simulate(
            input(
                prepayments = listOf(
                    Prepayment(2, BigDecimal("999999"), PrepaymentMode.REDUCE_TERM)
                )
            )
        )

        assertThat(result.schedule.size).isEqualTo(2)
        assertThat(result.schedule.last().closingBalanceUf.toDouble()).isWithin(0.0001).of(0.0)
        assertThat(result.totalPrepaymentsUf).isLessThan(BigDecimal("4000"))
    }

    /** With no fees and no insurance the CAE is just the annualised rate. */
    @Test
    fun `CAE without costs equals the effective annual rate`() {
        val result = MortgageEngine.simulate(input())
        val expected = (Math.pow(1 + 0.045 / 12, 12.0) - 1) * 100

        assertThat(result.caePct).isNotNull()
        assertThat(result.caePct!!.toDouble()).isWithin(0.02).of(expected)
    }

    @Test
    fun `CAE rises once fees and insurance are added`() {
        val bare = MortgageEngine.simulate(input())
        val loaded = MortgageEngine.simulate(
            input(life = "0.03", fire = "0.4", stamp = "0.8", other = "30")
        )

        assertThat(loaded.caePct!!).isGreaterThan(bare.caePct!!)
    }

    @Test
    fun `upfront costs are computed off the loan amount`() {
        val result = MortgageEngine.simulate(input(stamp = "0.8", origination = "5", other = "30"))

        // 4000 UF * 0.8% = 32 UF, plus 5 and 30.
        assertThat(result.upfrontCostsUf.toDouble()).isWithin(0.0001).of(67.0)
    }

    @Test
    fun `financed percentage reflects the down payment`() {
        assertThat(input().financedPct.toDouble()).isWithin(0.001).of(80.0)
    }

    @Test
    fun `total cost adds every outflow plus upfront costs`() {
        val result = MortgageEngine.simulate(input(life = "0.03", fire = "0.4", stamp = "0.8"))
        val sumOutflows = result.schedule.fold(BigDecimal.ZERO) { acc, r -> acc + r.totalOutflowUf }

        assertThat(result.totalCostUf.setScale(2, RoundingMode.HALF_UP))
            .isEqualTo((sumOutflows + result.upfrontCostsUf).setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `a longer term lowers the payment and raises total interest`() {
        val short = MortgageEngine.simulate(input(years = 15))
        val long = MortgageEngine.simulate(input(years = 30))

        assertThat(long.basePaymentUf).isLessThan(short.basePaymentUf)
        assertThat(long.totalInterestUf).isGreaterThan(short.totalInterestUf)
    }

    // ------------------------------------------------------------- due dates

    @Test
    fun `the first instalment falls on the chosen date`() {
        val result = MortgageEngine.simulate(input(firstPayment = LocalDate.of(2027, 3, 20)))

        assertThat(result.schedule.first().date).isEqualTo(LocalDate.of(2027, 3, 20))
    }

    @Test
    fun `instalments advance one month at a time`() {
        val result = MortgageEngine.simulate(input(firstPayment = LocalDate.of(2026, 10, 5)))

        assertThat(result.schedule[1].date).isEqualTo(LocalDate.of(2026, 11, 5))
        assertThat(result.schedule[11].date).isEqualTo(LocalDate.of(2027, 9, 5))
        assertThat(result.schedule[12].date).isEqualTo(LocalDate.of(2027, 10, 5))
        assertThat(result.schedule.last().date).isEqualTo(LocalDate.of(2051, 9, 5))
    }

    /**
     * A 31st falls back to the last day of shorter months, which is how a
     * lender schedules it, and the day recovers afterwards.
     */
    @Test
    fun `a month-end due date clamps to shorter months`() {
        val result = MortgageEngine.simulate(input(firstPayment = LocalDate.of(2026, 1, 31)))

        assertThat(result.schedule[0].date).isEqualTo(LocalDate.of(2026, 1, 31))
        assertThat(result.schedule[1].date).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(result.schedule[2].date).isEqualTo(LocalDate.of(2026, 3, 31))
        assertThat(result.schedule[3].date).isEqualTo(LocalDate.of(2026, 4, 30))
    }

    @Test
    fun `the due date does not affect any amount`() {
        val a = MortgageEngine.simulate(input(firstPayment = LocalDate.of(2026, 10, 5)))
        val b = MortgageEngine.simulate(input(firstPayment = LocalDate.of(2031, 2, 17)))

        assertThat(b.basePaymentUf).isEqualTo(a.basePaymentUf)
        assertThat(b.totalCostUf).isEqualTo(a.totalCostUf)
        assertThat(b.caePct).isEqualTo(a.caePct)
    }
}
