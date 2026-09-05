package cl.ufchile.app.domain

import cl.ufchile.app.domain.engine.UfEngine
import cl.ufchile.app.domain.model.UfValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class UfEngineTest {

    private val today = LocalDate.of(2026, 9, 5)

    private val series = listOf(
        UfValue(LocalDate.of(2026, 9, 3), BigDecimal("40877.73")),
        UfValue(LocalDate.of(2026, 9, 4), BigDecimal("40879.04")),
        UfValue(LocalDate.of(2026, 9, 5), BigDecimal("40880.36")),
        UfValue(LocalDate.of(2026, 9, 6), BigDecimal("40881.68")),
        UfValue(LocalDate.of(2026, 9, 9), BigDecimal("40885.63")),
    )

    @Test
    fun `current value ignores published future dates`() {
        val current = UfEngine.currentOf(series, today)

        assertThat(current).isNotNull()
        assertThat(current!!.date).isEqualTo(today)
        assertThat(current.value).isEqualTo(BigDecimal("40880.36"))
    }

    @Test
    fun `future values are the ones beyond today`() {
        val future = UfEngine.futureOf(series, today)

        assertThat(future).hasSize(2)
        assertThat(future.first().date).isEqualTo(LocalDate.of(2026, 9, 6))
        assertThat(future.map { it.date }).isInOrder()
    }

    @Test
    fun `isFuture marks published-but-not-yet-reached days`() {
        assertThat(series.first().isFuture(today)).isFalse()
        assertThat(series.last().isFuture(today)).isTrue()
    }

    @Test
    fun `conversions round trip`() {
        val rate = BigDecimal("40880.36")
        val uf = UfEngine.clpToUf(BigDecimal("1000000"), rate)
        val back = UfEngine.ufToClp(uf, rate)

        assertThat(uf.toDouble()).isWithin(0.0001).of(1000000 / 40880.36)
        assertThat(back.toDouble()).isWithin(5.0).of(1000000.0)
    }

    @Test
    fun `conversion with a zero rate does not divide by zero`() {
        assertThat(UfEngine.clpToUf(BigDecimal("1000"), BigDecimal.ZERO))
            .isEqualTo(BigDecimal.ZERO)
        assertThat(UfEngine.deltaPct(BigDecimal.ZERO, BigDecimal("10")))
            .isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `deltas carry the right sign`() {
        assertThat(UfEngine.delta(BigDecimal("100"), BigDecimal("102")))
            .isEqualTo(BigDecimal("2"))
        assertThat(UfEngine.deltaPct(BigDecimal("100"), BigDecimal("102")).toDouble())
            .isWithin(1e-9).of(2.0)
        assertThat(UfEngine.deltaPct(BigDecimal("100"), BigDecimal("98")).toDouble())
            .isWithin(1e-9).of(-2.0)
    }

    @Test
    fun `an empty series has no current value`() {
        assertThat(UfEngine.currentOf(emptyList(), today)).isNull()
        assertThat(UfEngine.futureOf(emptyList(), today)).isEmpty()
    }

    // --------------------------------------------------------- annualisation

    @Test
    fun `a full year annualises to its own change`() {
        val annual = UfEngine.annualisedPct(BigDecimal("100"), BigDecimal("104"), 365)

        assertThat(annual.toDouble()).isWithin(0.05).of(4.0)
    }

    @Test
    fun `a half year annualises to roughly the compounded rate`() {
        // 2% in six months compounds to about 4.04% a year.
        val annual = UfEngine.annualisedPct(BigDecimal("100"), BigDecimal("102"), 183)

        assertThat(annual.toDouble()).isWithin(0.1).of(4.04)
    }

    @Test
    fun `a decline annualises negative`() {
        val annual = UfEngine.annualisedPct(BigDecimal("100"), BigDecimal("98"), 365)

        assertThat(annual.toDouble()).isWithin(0.05).of(-2.0)
    }

    @Test
    fun `annualising a short window magnifies the move, as it should`() {
        // 1% in a month is a much larger annual rate; the UI labels it so.
        val annual = UfEngine.annualisedPct(BigDecimal("100"), BigDecimal("101"), 30)

        assertThat(annual.toDouble()).isGreaterThan(12.0)
    }

    @Test
    fun `annualisation refuses impossible inputs instead of returning nonsense`() {
        assertThat(UfEngine.annualisedPct(BigDecimal("100"), BigDecimal("110"), 0))
            .isEqualTo(BigDecimal.ZERO)
        assertThat(UfEngine.annualisedPct(BigDecimal.ZERO, BigDecimal("110"), 365))
            .isEqualTo(BigDecimal.ZERO)
        assertThat(UfEngine.annualisedPct(BigDecimal("100"), BigDecimal.ZERO, 365))
            .isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `real UF movement annualises close to Chilean inflation`() {
        // 5-sep-2025 to 5-sep-2026 in the real series.
        val annual = UfEngine.annualisedPct(BigDecimal("39434.60"), BigDecimal("40880.36"), 365)

        assertThat(annual.toDouble()).isGreaterThan(2.0)
        assertThat(annual.toDouble()).isLessThan(6.0)
    }
}
