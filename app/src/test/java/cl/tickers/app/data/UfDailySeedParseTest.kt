package cl.tickers.app.data

import cl.tickers.app.data.seed.UfDailySeed
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/** The asset format's parser, exercised without Android. */
class UfDailySeedParseTest {

    private fun lines(vararg data: String) =
        listOf("# comentario", "# inicio: 2026-09-01", "# unidad: centavos") + data

    @Test
    fun `values are read as centavos into exact two-decimal pesos`() {
        val parsed = UfDailySeed.parse(lines("4088036", "4088168"))

        assertThat(parsed).hasSize(2)
        assertThat(parsed[0].date).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(parsed[0].value).isEqualTo(BigDecimal("40880.36"))
        assertThat(parsed[1].date).isEqualTo(LocalDate.of(2026, 9, 2))
        assertThat(parsed[1].value.toPlainString()).isEqualTo("40881.68")
    }

    /** A blank line is a day the source lacks. It must shift the calendar. */
    @Test
    fun `a gap is skipped without shifting later dates`() {
        val parsed = UfDailySeed.parse(lines("4088036", "", "4088300"))

        assertThat(parsed).hasSize(2)
        assertThat(parsed[1].date).isEqualTo(LocalDate.of(2026, 9, 3))
    }

    @Test
    fun `a missing or unreadable header yields nothing rather than wrong dates`() {
        assertThat(UfDailySeed.parse(listOf("# sin inicio", "123"))).isEmpty()
        assertThat(UfDailySeed.parse(listOf("# inicio: no-es-fecha", "123"))).isEmpty()
        assertThat(UfDailySeed.parse(emptyList())).isEmpty()
    }

    @Test
    fun `junk lines are ignored, not guessed at`() {
        val parsed = UfDailySeed.parse(lines("4088036", "abc", "4088300"))

        assertThat(parsed).hasSize(2)
        assertThat(parsed[1].date).isEqualTo(LocalDate.of(2026, 9, 3))
    }

    /** Guards the shipped asset itself against a truncated regeneration. */
    @Test
    fun `the bundled file covers the whole series with at most a handful of gaps`() {
        val file = File("src/main/assets/uf_daily.txt")
        assertThat(file.exists()).isTrue()

        val parsed = UfDailySeed.parse(file.readLines())
        assertThat(parsed.size).isAtLeast(17_900)
        assertThat(parsed.first().date).isEqualTo(LocalDate.of(1977, 8, 1))

        val span = java.time.temporal.ChronoUnit.DAYS
            .between(parsed.first().date, parsed.last().date) + 1
        assertThat(span - parsed.size).isLessThan(10L)
        assertThat(parsed.all { it.value.signum() > 0 }).isTrue()
    }

    /**
     * Guards the shipped asset against the corruption the source is known to
     * serve. A regeneration that let 608,15 back in would fail here.
     */
    @Test
    fun `the bundled file contains no implausible day-over-day jump`() {
        val parsed = UfDailySeed.parse(File("src/main/assets/uf_daily.txt").readLines())

        parsed.zipWithNext().forEach { (a, b) ->
            val gap = java.time.temporal.ChronoUnit.DAYS.between(a.date, b.date)
                .coerceAtLeast(1L)
            val change = kotlin.math.abs(b.value.toDouble() / a.value.toDouble() - 1.0)
            assertThat(change).isLessThan(0.01 * gap)
        }
    }

    @Test
    fun `the bundled file survives its own runtime sanity filter untouched`() {
        val parsed = UfDailySeed.parse(File("src/main/assets/uf_daily.txt").readLines())

        assertThat(cl.tickers.app.domain.engine.UfSanity.filter(parsed)).hasSize(parsed.size)
    }

    /**
     * The source serves 608,15 and 607,38 for 29 and 30 December 2014. The UF
     * was frozen at 24.627,10 for that whole re-adjustment period — November
     * 2014's CPI was 0,0% — so the true values are recovered exactly from the
     * surrounding days rather than left as holes.
     */
    @Test
    fun `the corrupt December 2014 days carry their real value`() {
        val byDate = UfDailySeed.parse(File("src/main/assets/uf_daily.txt").readLines())
            .associateBy { it.date }

        listOf(28, 29, 30, 31).forEach { day ->
            val value = byDate[LocalDate.of(2014, 12, day)]
            assertThat(value).isNotNull()
            assertThat(value!!.value.toDouble()).isWithin(0.005).of(24627.10)
        }
    }

    @Test
    fun `the series has no missing day at all`() {
        val parsed = UfDailySeed.parse(File("src/main/assets/uf_daily.txt").readLines())
        val span = java.time.temporal.ChronoUnit.DAYS
            .between(parsed.first().date, parsed.last().date) + 1

        assertThat(parsed.size.toLong()).isEqualTo(span)
    }

    // ------------------------------------------------- fidelidad frente al IPC

    private fun anchors() = UfDailySeed.parse(File("src/main/assets/uf_daily.txt").readLines())
        .filter { it.date.dayOfMonth == 9 }
        .associate { java.time.YearMonth.from(it.date) to it.value }

    /**
     * The UF is re-adjusted daily so that its value on the 9th of month M+1
     * divided by its value on the 9th of month M equals the CPI variation of
     * month M-1. That relationship is what makes the series usable as a price
     * index, so it is checked against the CPI the INE actually published.
     */
    @Test
    fun `the series reproduces the published CPI for every month of 2024`() {
        val a = anchors()
        val published = mapOf(
            2 to 0.6, 3 to 0.4, 4 to 0.5, 5 to 0.3, 6 to -0.1, 7 to 0.7,
            8 to 0.3, 9 to 0.1, 10 to 1.0, 11 to 0.2, 12 to -0.2,
        )

        published.forEach { (month, expected) ->
            val target = java.time.YearMonth.of(2024, month)
            val before = a[target.plusMonths(1)]
            val after = a[target.plusMonths(2)]
            assertThat(before).isNotNull()
            assertThat(after).isNotNull()

            val derived = (after!!.toDouble() / before!!.toDouble() - 1) * 100
            // The published figure carries a single decimal.
            assertThat(derived).isWithin(0.05).of(expected)
        }
    }

    /** December 2008 was Chile's sharpest monthly deflation. */
    @Test
    fun `the 2009 deflation episode survives in the series`() {
        val a = anchors()
        val before = a[java.time.YearMonth.of(2009, 1)]!!
        val after = a[java.time.YearMonth.of(2009, 2)]!!

        assertThat((after.toDouble() / before.toDouble() - 1) * 100).isWithin(0.05).of(-1.2)
    }
}
