package cl.tickers.app.ui

import cl.tickers.app.domain.engine.UfReajuste
import cl.tickers.app.domain.model.UfValue
import cl.tickers.app.ui.inflation.ShareReajuste
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ShareReajusteTest {

    private val result = UfReajuste.convert(
        amount = BigDecimal("4000"),
        from = UfValue(LocalDate.of(1990, 1, 1), BigDecimal("5435.28")),
        to = UfValue(LocalDate.of(2026, 9, 6), BigDecimal("40881.68")),
    )
    private val message = ShareReajuste.buildMessage(result)

    /** The equivalence is what gets quoted, so it leads. */
    @Test
    fun `the equivalence comes first`() {
        val lines = message.lines()

        assertThat(lines[0]).isEqualTo("*Equivalencia de valores*")
        assertThat(lines[2]).isEqualTo("$4.000 del 1 de enero de 1990")
        assertThat(lines[3]).isEqualTo("equivalen a")
        assertThat(lines[4]).isEqualTo("$30.086 del 6 de septiembre de 2026")
    }

    @Test
    fun `the arithmetic behind it is included so it can be checked`() {
        assertThat(message).contains("Reajuste: 7,5215x")
        assertThat(message).contains("Variación acumulada: 652,15%")
        assertThat(message).contains("Equivalente anual: 5,66%")
        assertThat(message).contains("Período: 13.397 días (36,7 años)")
    }

    @Test
    fun `both UF values and the amount in UF are shown`() {
        assertThat(message).contains("UF el 01-01-1990: $5.435,28")
        assertThat(message).contains("Equivale a: 0,7359 UF")
        assertThat(message).contains("UF el 06-09-2026: $40.881,68")
    }

    /** Every figure carries its Chilean separators, chat included. */
    @Test
    fun `no figure escapes unpunctuated`() {
        assertThat(message).doesNotContain("30086")
        assertThat(message).doesNotContain("13397")
        assertThat(message).doesNotContain("40881.68")
    }

    @Test
    fun `the method is stated rather than left implied`() {
        assertThat(message).contains("Reajustado con la UF")
    }

    @Test
    fun `emphasis uses the marks chat apps understand`() {
        assertThat(message).startsWith("*")
        assertThat(message).contains("*Cálculo por UF*")
    }

    @Test
    fun `a same-day restatement still produces a coherent message`() {
        val day = UfValue(LocalDate.of(2026, 9, 6), BigDecimal("40881.68"))
        val text = ShareReajuste.buildMessage(UfReajuste.convert(BigDecimal("1000"), day, day))

        assertThat(text).contains("$1.000 del 6 de septiembre de 2026")
        assertThat(text).contains("Reajuste: 1,0000x")
        assertThat(text).contains("Período: 0 días")
    }
}
