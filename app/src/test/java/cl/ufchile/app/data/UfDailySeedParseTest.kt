package cl.ufchile.app.data

import cl.ufchile.app.data.seed.UfDailySeed
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

        assertThat(cl.ufchile.app.domain.engine.UfSanity.filter(parsed)).hasSize(parsed.size)
    }
}
