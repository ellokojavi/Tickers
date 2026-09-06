package cl.ufchile.app.core

import androidx.compose.ui.text.AnnotatedString
import cl.ufchile.app.core.format.ThousandsTransformation
import cl.ufchile.app.core.format.sanitizeNumericInput
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThousandsTransformationTest {

    private fun render(raw: String, decimals: Boolean = true) =
        ThousandsTransformation(decimals).filter(AnnotatedString(raw)).text.text

    private fun mapping(raw: String, decimals: Boolean = true) =
        ThousandsTransformation(decimals).filter(AnnotatedString(raw)).offsetMapping

    // ------------------------------------------------------------ rendering

    @Test
    fun `thousands are grouped with dots`() {
        assertThat(render("5000")).isEqualTo("5.000")
        assertThat(render("1234567")).isEqualTo("1.234.567")
        assertThat(render("204401800")).isEqualTo("204.401.800")
    }

    @Test
    fun `short numbers are left alone`() {
        assertThat(render("")).isEqualTo("")
        assertThat(render("5")).isEqualTo("5")
        assertThat(render("999")).isEqualTo("999")
    }

    @Test
    fun `only the integer part is grouped`() {
        assertThat(render("1234567,89")).isEqualTo("1.234.567,89")
        assertThat(render("4,5")).isEqualTo("4,5")
        assertThat(render("0,03")).isEqualTo("0,03")
    }

    @Test
    fun `a trailing separator survives so the user can keep typing`() {
        assertThat(render("5000,")).isEqualTo("5.000,")
    }

    @Test
    fun `negatives keep their sign`() {
        assertThat(render("-1234")).isEqualTo("-1.234")
        assertThat(render("-2,5")).isEqualTo("-2,5")
    }

    @Test
    fun `an integer-only field never shows a decimal part`() {
        assertThat(render("25", decimals = false)).isEqualTo("25")
        assertThat(render("1000000", decimals = false)).isEqualTo("1.000.000")
    }

    // -------------------------------------------------------------- cursor

    @Test
    fun `the cursor maps across inserted separators`() {
        val m = mapping("1234567")

        assertThat(m.originalToTransformed(0)).isEqualTo(0)
        assertThat(m.originalToTransformed(1)).isEqualTo(1)   // "1"
        assertThat(m.originalToTransformed(4)).isEqualTo(5)   // "1.234"
        assertThat(m.originalToTransformed(7)).isEqualTo(9)   // "1.234.567"
    }

    @Test
    fun `the cursor mapping round trips`() {
        val m = mapping("1234567,89")

        for (o in 0..10) {
            assertThat(m.transformedToOriginal(m.originalToTransformed(o))).isEqualTo(o)
        }
    }

    /** Compose rejects a mapping that leaves the text's bounds. */
    @Test
    fun `mappings stay inside bounds for every input`() {
        listOf("", "5", "5000", "-1234567,89", "0,").forEach { raw ->
            val transformation = ThousandsTransformation(true)
            val result = transformation.filter(AnnotatedString(raw))
            val m = result.offsetMapping
            for (o in 0..raw.length) {
                assertThat(m.originalToTransformed(o)).isIn(0..result.text.length)
            }
            for (t in 0..result.text.length) {
                assertThat(m.transformedToOriginal(t)).isIn(0..raw.length)
            }
        }
    }

    // ------------------------------------------------------------ sanitising

    /**
     * The regression this exists for: a typed "." used to be accepted and then
     * discarded when parsing, so "4.5" in a rate field silently became 45.
     */
    @Test
    fun `a typed dot becomes the decimal separator`() {
        assertThat(sanitizeNumericInput("4.5", allowDecimals = true)).isEqualTo("4,5")
        assertThat(sanitizeNumericInput("0.03", allowDecimals = true)).isEqualTo("0,03")
    }

    @Test
    fun `only the first separator counts`() {
        assertThat(sanitizeNumericInput("1,2,3", allowDecimals = true)).isEqualTo("1,23")
        assertThat(sanitizeNumericInput("1.2.3", allowDecimals = true)).isEqualTo("1,23")
    }

    @Test
    fun `a leading separator is dropped rather than starting a number`() {
        assertThat(sanitizeNumericInput(",5", allowDecimals = true)).isEqualTo("5")
        assertThat(sanitizeNumericInput(".5", allowDecimals = true)).isEqualTo("5")
    }

    @Test
    fun `integer fields refuse any separator`() {
        assertThat(sanitizeNumericInput("25,5", allowDecimals = false)).isEqualTo("255")
        assertThat(sanitizeNumericInput("1.000", allowDecimals = false)).isEqualTo("1000")
    }

    @Test
    fun `letters and symbols never make it in`() {
        assertThat(sanitizeNumericInput("1a2b3", allowDecimals = true)).isEqualTo("123")
        assertThat(sanitizeNumericInput("\$5.000", allowDecimals = true)).isEqualTo("5,000")
        assertThat(sanitizeNumericInput("abc", allowDecimals = true)).isEmpty()
    }

    @Test
    fun `a leading minus is preserved`() {
        assertThat(sanitizeNumericInput("-2,5", allowDecimals = true)).isEqualTo("-2,5")
    }

    /** What the field holds must be what the parser understands. */
    @Test
    fun `sanitised text parses back to the intended number`() {
        val raw = sanitizeNumericInput("1234567.89", allowDecimals = true)

        assertThat(raw).isEqualTo("1234567,89")
        assertThat(cl.ufchile.app.core.format.Fmt.parseNumber(raw))
            .isEqualTo(java.math.BigDecimal("1234567.89"))
    }

    // --------------------------------------------- state that is not canonical

    /**
     * The regression this exists for. The inflation screen's amount defaulted
     * to "4.000" — already grouped — and the transformation counted the dot as
     * a digit position, inserting a second one and rendering "4..000".
     *
     * Every test here fed it clean input, so nothing caught it, and the screen
     * still computed the right answer because the parser discards dots. The
     * transformation must therefore cope with a state it did not produce.
     */
    @Test
    fun `an already-grouped state never doubles its separators`() {
        assertThat(render("4.000", decimals = false)).isEqualTo("4.000")
        assertThat(render("1.234.567", decimals = false)).isEqualTo("1.234.567")
        assertThat(render("40.880", decimals = false)).isEqualTo("40.880")
    }

    @Test
    fun `garbage in the state still renders something sane`() {
        assertThat(render("abc")).isEmpty()
        assertThat(render("1a2b3")).isEqualTo("123")
        assertThat(render("$5000")).isEqualTo("5.000")
    }

    @Test
    fun `mappings stay in bounds even for a state that is not canonical`() {
        listOf("4.000", "1.234.567", "1a2b3", "$5000", ",,,").forEach { raw ->
            listOf(true, false).forEach { decimals ->
                val result = ThousandsTransformation(decimals).filter(AnnotatedString(raw))
                val m = result.offsetMapping
                for (o in 0..raw.length) {
                    assertThat(m.originalToTransformed(o)).isIn(0..result.text.length)
                }
                for (x in 0..result.text.length) {
                    assertThat(m.transformedToOriginal(x)).isIn(0..raw.length)
                }
            }
        }
    }
}
