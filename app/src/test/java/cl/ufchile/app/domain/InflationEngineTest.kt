package cl.ufchile.app.domain

import cl.ufchile.app.data.seed.UfDailySeed
import cl.ufchile.app.domain.engine.InflationEngine
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.YearMonth

class InflationEngineTest {

    private lateinit var engine: InflationEngine
    private lateinit var anchors: Map<YearMonth, BigDecimal>

    @Before
    fun setUp() {
        // Reads the very asset that ships in the APK, through the very parser
        // the app uses, so a corrupted or truncated regeneration fails the
        // build instead of the phone.
        val file = File("src/main/assets/uf_daily.txt")
        assertThat(file.exists()).isTrue()
        anchors = UfDailySeed.parse(file.readLines())
            .filter { it.date.dayOfMonth == 9 }
            .associate { YearMonth.from(it.date) to it.value }
        engine = InflationEngine(anchors)
    }

    @Test
    fun `seed covers the whole UF era`() {
        assertThat(anchors).isNotEmpty()
        assertThat(anchors.keys.min()).isEqualTo(YearMonth.of(1977, 8))
        assertThat(anchors.keys.max()).isAtLeast(YearMonth.of(2026, 1))
        // No month may be missing inside the covered span.
        var cursor = anchors.keys.min()
        val last = anchors.keys.max()
        while (cursor < last) {
            assertThat(anchors).containsKey(cursor)
            cursor = cursor.plusMonths(1)
        }
    }

    /**
     * The UF does fall during deflation, so a monotonic series would be wrong.
     * What must hold is that no month-over-month step is implausible, which is
     * what a truncated or misparsed dataset would produce.
     */
    @Test
    fun `no month-over-month step is implausible`() {
        val sorted = anchors.toSortedMap()
        sorted.entries.zipWithNext().forEach { (a, b) ->
            val pct = (b.value.toDouble() / a.value.toDouble() - 1) * 100
            assertThat(pct).isGreaterThan(-3.0)
            assertThat(pct).isLessThan(25.0) // the late-1970s peaks sit near 10%
        }
    }

    /** December 2008 was Chile's sharpest monthly deflation; the UF followed. */
    @Test
    fun `the 2009 deflation episode is present in the seed`() {
        val variation = engine.monthlyVariationPct(YearMonth.of(2008, 12))

        assertThat(variation).isNotNull()
        assertThat(variation!!.toDouble()).isWithin(0.05).of(-1.2)
    }

    /**
     * The whole method rests on this: the variation implied by the UF must
     * reproduce the CPI the INE actually published.
     */
    @Test
    fun `derived monthly variation matches the published CPI for 2024`() {
        val published = mapOf(
            2 to "0.6", 3 to "0.4", 4 to "0.5", 5 to "0.3", 6 to "-0.1", 7 to "0.7",
            8 to "0.3", 9 to "0.1", 10 to "1.0", 11 to "0.2", 12 to "-0.2",
        )
        published.forEach { (month, expected) ->
            val derived = engine.monthlyVariationPct(YearMonth.of(2024, month))
            assertThat(derived).isNotNull()
            assertThat(derived!!.toDouble())
                .isWithin(0.05) // the published figure carries one decimal
                .of(expected.toDouble())
        }
    }

    @Test
    fun `converting forward and back returns the original amount`() {
        val amount = BigDecimal("100000")
        val forward = engine.convert(amount, YearMonth.of(1995, 6), YearMonth.of(2020, 6))
        val back = engine.convert(forward.adjustedAmount, YearMonth.of(2020, 6), YearMonth.of(1995, 6))

        assertThat(back.adjustedAmount.toDouble()).isWithin(1.0).of(amount.toDouble())
    }

    @Test
    fun `same month conversion is a no-op`() {
        val result = engine.convert(BigDecimal("4000"), YearMonth.of(2000, 1), YearMonth.of(2000, 1))

        assertThat(result.adjustedAmount.toDouble()).isWithin(0.5).of(4000.0)
        assertThat(result.factor.toDouble()).isWithin(1e-6).of(1.0)
        assertThat(result.cumulativePct.toDouble()).isWithin(1e-6).of(0.0)
        assertThat(result.months).isEqualTo(0)
    }

    @Test
    fun `1990 pesos are worth several times more today`() {
        val result = engine.convert(BigDecimal("4000"), YearMonth.of(1990, 1), YearMonth.of(2024, 12))

        // Chile's cumulative inflation over that span is roughly sevenfold.
        assertThat(result.factor.toDouble()).isGreaterThan(6.0)
        assertThat(result.factor.toDouble()).isLessThan(8.0)
        assertThat(result.months).isEqualTo(419)
        assertThat(result.annualizedPct.toDouble()).isGreaterThan(4.0)
        assertThat(result.annualizedPct.toDouble()).isLessThan(7.0)
    }

    @Test
    fun `annualised rate reproduces the cumulative factor`() {
        val result = engine.convert(BigDecimal("1000"), YearMonth.of(2000, 1), YearMonth.of(2020, 1))
        val years = result.months / 12.0
        val rebuilt = Math.pow(1 + result.annualizedPct.toDouble() / 100, years)

        assertThat(rebuilt).isWithin(0.01).of(result.factor.toDouble())
    }

    @Test
    fun `months outside coverage are rejected instead of guessed`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.convert(BigDecimal("1000"), YearMonth.of(1950, 1), YearMonth.of(2020, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.convert(BigDecimal("1000"), YearMonth.of(2000, 1), engine.latestMonth.plusMonths(6))
        }
    }

    @Test
    fun `supports reports the real coverage window`() {
        assertThat(engine.supports(engine.earliestMonth)).isTrue()
        assertThat(engine.supports(engine.latestMonth)).isTrue()
        assertThat(engine.supports(engine.earliestMonth.minusMonths(1))).isFalse()
        assertThat(engine.supports(engine.latestMonth.plusMonths(1))).isFalse()
    }

    @Test
    fun `uf equivalence restates the amount through UF units`() {
        val result = engine.asUfEquivalence(
            amount = BigDecimal("4000"),
            ufAtOrigin = BigDecimal("5712.95"),
            ufAtTarget = BigDecimal("40880.36"),
        )

        assertThat(result.ufUnits.toDouble()).isWithin(0.001).of(4000 / 5712.95)
        assertThat(result.adjustedAmount.toDouble())
            .isWithin(2.0).of(4000 / 5712.95 * 40880.36)
    }
}
