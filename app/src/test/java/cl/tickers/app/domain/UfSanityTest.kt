package cl.tickers.app.domain

import cl.tickers.app.domain.engine.UfSanity
import cl.tickers.app.domain.model.UfValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class UfSanityTest {

    private fun uf(day: Int, value: String) =
        UfValue(LocalDate.of(2014, 12, day), BigDecimal(value))

    /** The exact corruption the public source actually serves. */
    @Test
    fun `the real 2014 corruption is rejected`() {
        val incoming = listOf(
            uf(28, "24627.10"),
            uf(29, "608.15"),
            uf(30, "607.38"),
            uf(31, "24627.10"),
        )

        val kept = UfSanity.filter(incoming)

        assertThat(kept.map { it.date.dayOfMonth }).containsExactly(28, 31).inOrder()
    }

    @Test
    fun `a normal series passes untouched`() {
        val incoming = listOf(
            uf(1, "24500.00"), uf(2, "24505.00"), uf(3, "24510.00"), uf(4, "24515.00"),
        )

        assertThat(UfSanity.filter(incoming)).hasSize(4)
    }

    /** 0,2633% is the largest real daily move in 49 years; it must survive. */
    @Test
    fun `the largest genuine daily move is kept`() {
        val incoming = listOf(uf(1, "1000.00"), uf(2, "1002.633"))

        assertThat(UfSanity.filter(incoming)).hasSize(2)
    }

    @Test
    fun `the allowance scales with the gap between days`() {
        // 1,5% across three days is within 1% per day.
        assertThat(UfSanity.filter(listOf(uf(1, "1000.00"), uf(4, "1015.00")))).hasSize(2)
        // 1,5% in a single day is not.
        assertThat(UfSanity.filter(listOf(uf(1, "1000.00"), uf(2, "1015.00")))).hasSize(1)
    }

    @Test
    fun `an anchor checks the first value of a batch too`() {
        val anchor = UfValue(LocalDate.of(2014, 12, 28), BigDecimal("24627.10"))

        val kept = UfSanity.filter(listOf(uf(29, "608.15")), anchor)

        assertThat(kept).isEmpty()
    }

    @Test
    fun `without an anchor the first value is trusted`() {
        assertThat(UfSanity.filter(listOf(uf(29, "608.15")))).hasSize(1)
    }

    @Test
    fun `non-positive values never pass`() {
        val kept = UfSanity.filter(listOf(uf(1, "0"), uf(2, "-5"), uf(3, "24500.00")))

        assertThat(kept).hasSize(1)
        assertThat(kept.single().date.dayOfMonth).isEqualTo(3)
    }

    @Test
    fun `input order does not matter and output is sorted`() {
        val kept = UfSanity.filter(listOf(uf(3, "24510.00"), uf(1, "24500.00"), uf(2, "24505.00")))

        assertThat(kept.map { it.date.dayOfMonth }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `an empty batch stays empty`() {
        assertThat(UfSanity.filter(emptyList())).isEmpty()
    }
}
