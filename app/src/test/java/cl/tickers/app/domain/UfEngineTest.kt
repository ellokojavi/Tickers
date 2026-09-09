package cl.tickers.app.domain

import cl.tickers.app.domain.engine.UfEngine
import cl.tickers.app.domain.model.DatedValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class UfEngineTest {

    private val today = LocalDate.of(2026, 9, 5)

    private val series = listOf(
        DatedValue(LocalDate.of(2026, 9, 3), BigDecimal("40877.73")),
        DatedValue(LocalDate.of(2026, 9, 4), BigDecimal("40879.04")),
        DatedValue(LocalDate.of(2026, 9, 5), BigDecimal("40880.36")),
        DatedValue(LocalDate.of(2026, 9, 6), BigDecimal("40881.68")),
        DatedValue(LocalDate.of(2026, 9, 9), BigDecimal("40885.63")),
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

    // -------------------------------------------------------- downsampling

    @Test
    fun `a short series is returned untouched`() {
        val short = series.take(3)

        assertThat(UfEngine.downsample(short, 400)).isSameInstanceAs(short)
    }

    @Test
    fun `downsampling keeps the endpoints and the requested size`() {
        val long = (0 until 18_000).map {
            DatedValue(LocalDate.of(1977, 8, 1).plusDays(it.toLong()), BigDecimal(1000 + it))
        }

        val thinned = UfEngine.downsample(long, 400)

        assertThat(thinned).hasSize(400)
        assertThat(thinned.first()).isEqualTo(long.first())
        assertThat(thinned.last()).isEqualTo(long.last())
        assertThat(thinned.map { it.date }).isInOrder()
    }

    @Test
    fun `downsampling preserves the overall movement`() {
        val long = (0 until 18_000).map {
            DatedValue(LocalDate.of(1977, 8, 1).plusDays(it.toLong()), BigDecimal(1000 + it))
        }
        val thinned = UfEngine.downsample(long, 400)

        assertThat(UfEngine.deltaPct(thinned.first().value, thinned.last().value))
            .isEqualTo(UfEngine.deltaPct(long.first().value, long.last().value))
    }

    @Test
    fun `an absurd point budget does not crash`() {
        assertThat(UfEngine.downsample(series, 1)).isSameInstanceAs(series)
        assertThat(UfEngine.downsample(emptyList(), 400)).isEmpty()
    }

    // ------------------------------------------------------- history window

    private val longSeries = (0L until 400L).map {
        DatedValue(LocalDate.of(2025, 9, 5).plusDays(it), BigDecimal(40000 + it))
    }

    /**
     * The regression this pins: the chart ended on the last published day,
     * which is up to a month in the future, so its final label and its period
     * variation reached past today.
     */
    @Test
    fun `the window never reaches past today`() {
        val today = LocalDate.of(2026, 9, 5)

        val window = UfEngine.historyWindow(longSeries, months = 3, today = today)

        assertThat(window.last().date).isEqualTo(today)
        assertThat(window.none { it.date.isAfter(today) }).isTrue()
    }

    @Test
    fun `the whole history also stops at today`() {
        val today = LocalDate.of(2026, 9, 5)

        val window = UfEngine.historyWindow(longSeries, months = null, today = today)

        assertThat(window.first().date).isEqualTo(LocalDate.of(2025, 9, 5))
        assertThat(window.last().date).isEqualTo(today)
    }

    @Test
    fun `the window starts the requested number of months back`() {
        val today = LocalDate.of(2026, 9, 5)

        val window = UfEngine.historyWindow(longSeries, months = 3, today = today)

        assertThat(window.first().date).isEqualTo(LocalDate.of(2026, 6, 5))
    }

    @Test
    fun `today itself is included`() {
        val today = LocalDate.of(2026, 9, 5)

        assertThat(UfEngine.historyWindow(longSeries, months = 1, today = today).map { it.date })
            .contains(today)
    }

    @Test
    fun `a series entirely in the future yields nothing to chart`() {
        val future = listOf(
            DatedValue(LocalDate.of(2026, 9, 6), BigDecimal("40881.68")),
            DatedValue(LocalDate.of(2026, 9, 9), BigDecimal("40885.63")),
        )

        assertThat(UfEngine.historyWindow(future, months = null, today = LocalDate.of(2026, 9, 5)))
            .isEmpty()
    }

    @Test
    fun `an empty series stays empty`() {
        assertThat(UfEngine.historyWindow(emptyList(), months = 3)).isEmpty()
    }
}
