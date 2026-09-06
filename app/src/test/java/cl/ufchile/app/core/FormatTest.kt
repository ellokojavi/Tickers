package cl.ufchile.app.core

import cl.ufchile.app.core.format.Fmt
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FormatTest {

    @Test
    fun `pesos use Chilean separators`() {
        assertThat(Fmt.clp(BigDecimal("40880.36"))).isEqualTo("$40.880")
        assertThat(Fmt.clpExact(BigDecimal("40880.36"))).isEqualTo("$40.880,36")
        assertThat(Fmt.clp(BigDecimal("1234567"))).isEqualTo("$1.234.567")
    }

    @Test
    fun `uf keeps two decimals and four in tables`() {
        assertThat(Fmt.uf(BigDecimal("22.2331"))).isEqualTo("22,23 UF")
        assertThat(Fmt.uf4(BigDecimal("22.2331"))).isEqualTo("22,2331 UF")
    }

    @Test
    fun `signed values carry an explicit plus`() {
        assertThat(Fmt.pctSigned(BigDecimal("3.25"))).isEqualTo("+3,25%")
        assertThat(Fmt.pctSigned(BigDecimal("-3.25"))).isEqualTo("-3,25%")
        assertThat(Fmt.pctSigned(BigDecimal.ZERO)).isEqualTo("0,00%")
        assertThat(Fmt.clpSigned(BigDecimal("1.32"))).isEqualTo("+$1,32")
        assertThat(Fmt.clpSigned(BigDecimal("-1.32"))).isEqualTo("-$1,32")
    }

    @Test
    fun `parses Chilean formatted input`() {
        assertThat(Fmt.parseNumber("40.880,36")).isEqualTo(BigDecimal("40880.36"))
        assertThat(Fmt.parseNumber("$1.000")).isEqualTo(BigDecimal("1000"))
        assertThat(Fmt.parseNumber("4,5")).isEqualTo(BigDecimal("4.5"))
        assertThat(Fmt.parseNumber("1.234 UF")).isEqualTo(BigDecimal("1234"))
        assertThat(Fmt.parseNumber("-2,5")).isEqualTo(BigDecimal("-2.5"))
    }

    @Test
    fun `rejects input that is not a number`() {
        assertThat(Fmt.parseNumber("")).isNull()
        assertThat(Fmt.parseNumber("   ")).isNull()
        assertThat(Fmt.parseNumber("abc")).isNull()
        assertThat(Fmt.parseNumber(",")).isNull()
    }

    @Test
    fun `formatting and parsing round trip`() {
        val original = BigDecimal("1234567.89")
        val parsed = Fmt.parseNumber(Fmt.clpExact(original))

        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun `dates render in Spanish`() {
        val date = LocalDate.of(2026, 9, 5)

        assertThat(Fmt.shortDate(date)).isEqualTo("05-09-2026")
        assertThat(Fmt.longDate(date)).contains("septiembre")
        assertThat(Fmt.longDate(date)).startsWith("5 de")
    }

    @Test
    fun `relative days read naturally`() {
        val today = LocalDate.of(2026, 9, 5)

        assertThat(Fmt.relativeDay(today, today)).isEqualTo("hoy")
        assertThat(Fmt.relativeDay(today.minusDays(1), today)).isEqualTo("ayer")
        assertThat(Fmt.relativeDay(today.minusDays(4), today)).isEqualTo("hace 4 días")
        assertThat(Fmt.relativeDay(today.plusDays(1), today)).isEqualTo("mañana")
        assertThat(Fmt.relativeDay(today.plusDays(3), today)).isEqualTo("en 3 días")
    }

    @Test
    fun `counts are grouped like any other figure`() {
        assertThat(Fmt.integer(0)).isEqualTo("0")
        assertThat(Fmt.integer(480)).isEqualTo("480")
        assertThat(Fmt.integer(13_396L)).isEqualTo("13.396")
        assertThat(Fmt.integer(17_937)).isEqualTo("17.937")
        assertThat(Fmt.integer(-1_500)).isEqualTo("-1.500")
    }

    @Test
    fun `one-decimal spans use a comma and no percent sign`() {
        assertThat(Fmt.decimal1(36.7)).isEqualTo("36,7")
        assertThat(Fmt.decimal1(1.0)).isEqualTo("1,0")
        assertThat(Fmt.decimal1(1234.56)).isEqualTo("1.234,6")
    }
}
