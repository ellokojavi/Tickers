package cl.ufchile.app.ui

import cl.ufchile.app.core.format.sanitizeNumericInput
import cl.ufchile.app.ui.credit.CreditForm
import cl.ufchile.app.ui.inflation.InflationUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Numeric fields hold raw text; grouping belongs to the display. That was a
 * convention rather than something enforced, and one screen's default slipped
 * through already grouped, which the transformation then grouped again,
 * rendering "4.000" as "4..000".
 *
 * This pins the invariant for every default the app ships: passing it through
 * the sanitiser must be a no-op.
 */
class NumericDefaultsTest {

    private fun assertRaw(label: String, value: String, allowDecimals: Boolean = true) {
        assertThat(sanitizeNumericInput(value, allowDecimals))
            .isEqualTo(value)
    }

    @Test
    fun `every mortgage form default is raw`() {
        val f = CreditForm()

        assertRaw("valor de la propiedad", f.propertyValueUf)
        assertRaw("pie", f.downPaymentUf)
        assertRaw("tasa anual", f.annualRatePct)
        assertRaw("plazo", f.termYears, allowDecimals = false)
        assertRaw("desgravamen", f.lifeInsuranceMonthlyPct)
        assertRaw("incendio y sismo", f.fireInsuranceMonthlyUf)
        assertRaw("comisión", f.originationFeeUf)
        assertRaw("impuesto de timbres", f.stampTaxPct)
        assertRaw("gastos operacionales", f.otherUpfrontCostsUf)
    }

    /** The one that actually shipped wrong. */
    @Test
    fun `the inflation amount default is raw`() {
        assertRaw("monto en pesos", InflationUiState().amountText, allowDecimals = false)
    }

    @Test
    fun `the prepayment dialog defaults are raw`() {
        // Mirrors PrepaymentDialog's remembered initial values.
        assertRaw("cuota", "12", allowDecimals = false)
        assertRaw("monto del abono", "100")
    }

    @Test
    fun `the converter defaults are raw`() {
        assertRaw("UF", "1")
        assertRaw("pesos", "", allowDecimals = false)
    }
}
