package cl.tickers.app.domain

import cl.tickers.app.domain.engine.UfReajuste
import cl.tickers.app.domain.model.DatedValue
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class UfReajusteTest {

    private fun uf(date: String, value: String) = DatedValue(LocalDate.parse(date), BigDecimal(value))

    @Test
    fun `an amount is restated by the ratio of the two UF values`() {
        val result = UfReajuste.convert(
            amount = BigDecimal("1000"),
            from = uf("2020-01-01", "28000.00"),
            to = uf("2021-01-01", "29000.00"),
        )

        assertThat(result.factor.toDouble()).isWithin(1e-6).of(29000.0 / 28000.0)
        assertThat(result.adjustedAmount.toDouble()).isWithin(0.5).of(1000.0 * 29000 / 28000)
        assertThat(result.ufUnits.toDouble()).isWithin(0.0001).of(1000.0 / 28000.0)
    }

    /** The case the screen opens on. */
    @Test
    fun `four thousand pesos of january 1990 restate to about thirty thousand`() {
        val result = UfReajuste.convert(
            amount = BigDecimal("4000"),
            from = uf("1990-01-01", "5435.28"),
            to = uf("2026-09-05", "40880.36"),
        )

        assertThat(result.adjustedAmount.toDouble()).isWithin(1.0).of(30085.0)
        assertThat(result.factor.toDouble()).isWithin(0.0001).of(7.5213)
        assertThat(result.days).isEqualTo(13396L)
        assertThat(result.annualisedPct.toDouble()).isWithin(0.05).of(5.66)
    }

    @Test
    fun `the same day is a no-op`() {
        val day = uf("2020-05-05", "28000.00")

        val result = UfReajuste.convert(BigDecimal("1234"), day, day)

        assertThat(result.adjustedAmount.toDouble()).isWithin(0.5).of(1234.0)
        assertThat(result.factor.toDouble()).isWithin(1e-9).of(1.0)
        assertThat(result.variationPct.toDouble()).isWithin(1e-9).of(0.0)
        assertThat(result.annualisedPct.toDouble()).isWithin(1e-9).of(0.0)
        assertThat(result.days).isEqualTo(0L)
    }

    @Test
    fun `converting forward and back returns the original amount`() {
        val a = uf("1995-06-15", "12000.00")
        val b = uf("2020-06-15", "28000.00")

        val forward = UfReajuste.convert(BigDecimal("100000"), a, b)
        val back = UfReajuste.convert(forward.adjustedAmount, b, a)

        assertThat(back.adjustedAmount.toDouble()).isWithin(1.0).of(100000.0)
    }

    @Test
    fun `going backwards in time shrinks the amount and reports negative variation`() {
        val result = UfReajuste.convert(
            amount = BigDecimal("30000"),
            from = uf("2026-09-05", "40880.36"),
            to = uf("1990-01-01", "5435.28"),
        )

        assertThat(result.adjustedAmount.toDouble()).isLessThan(30000.0)
        assertThat(result.variationPct.signum()).isEqualTo(-1)
        assertThat(result.days).isEqualTo(-13396L)
    }

    @Test
    fun `the annual rate compounds back to the factor`() {
        val result = UfReajuste.convert(
            amount = BigDecimal("1000"),
            from = uf("2000-01-01", "15000.00"),
            to = uf("2020-01-01", "28000.00"),
        )
        val years = result.days / 365.25
        val rebuilt = Math.pow(1 + result.annualisedPct.toDouble() / 100, years)

        assertThat(rebuilt).isWithin(0.01).of(result.factor.toDouble())
    }

    /** Day precision is the point: consecutive days do not give the same answer. */
    @Test
    fun `two dates one day apart give different results`() {
        val base = uf("2026-09-01", "40875.09")
        val a = UfReajuste.convert(BigDecimal("1000000"), base, uf("2026-09-04", "40879.04"))
        val b = UfReajuste.convert(BigDecimal("1000000"), base, uf("2026-09-05", "40880.36"))

        assertThat(b.adjustedAmount).isGreaterThan(a.adjustedAmount)
    }

    @Test
    fun `large amounts do not drift from double rounding`() {
        val result = UfReajuste.convert(
            amount = BigDecimal("1000000000"),
            from = uf("2020-01-01", "28000.00"),
            to = uf("2020-01-02", "28001.00"),
        )

        // Computed from the factor once, not by rounding UF units and back.
        assertThat(result.adjustedAmount.toDouble())
            .isWithin(1.0).of(1_000_000_000.0 * 28001 / 28000)
    }

    @Test
    fun `a non-positive UF value is refused rather than dividing by zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            UfReajuste.convert(BigDecimal("1000"), uf("2020-01-01", "0"), uf("2021-01-01", "1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UfReajuste.convert(BigDecimal("1000"), uf("2020-01-01", "1"), uf("2021-01-01", "-1"))
        }
    }
}
