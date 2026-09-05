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
}
