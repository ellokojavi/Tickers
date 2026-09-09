package cl.tickers.app.domain.engine

import cl.tickers.app.domain.model.ReajusteResult
import cl.tickers.app.domain.model.DatedValue
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.temporal.ChronoUnit

/**
 * Restates an amount between two dates through the UF.
 *
 * This is the mechanism Chilean contracts, rents and debts are actually
 * re-adjusted with, and because the UF is published every calendar day it
 * answers at day precision. The UF tracks the CPI with the lag it is built
 * with — a day's value follows the CPI of two months earlier — so the
 * variation it reports is inflation as the UF carries it, not the INE's
 * month-on-month figure.
 */
object UfReajuste {

    private val MC = MathContext.DECIMAL64
    private const val DAYS_IN_YEAR = 365.25

    fun convert(amount: BigDecimal, from: DatedValue, to: DatedValue): ReajusteResult {
        require(from.value.signum() > 0 && to.value.signum() > 0) {
            "El valor de la UF debe ser positivo"
        }

        val units = amount.divide(from.value, 4, RoundingMode.HALF_UP)
        val factor = to.value.divide(from.value, MC)
        val days = ChronoUnit.DAYS.between(from.date, to.date)

        val annualised = if (days == 0L) BigDecimal.ZERO else {
            val years = days.toDouble() / DAYS_IN_YEAR
            val a = Math.pow(factor.toDouble(), 1.0 / years) - 1.0
            if (a.isNaN() || a.isInfinite()) BigDecimal.ZERO
            else BigDecimal(a * 100).setScale(2, RoundingMode.HALF_UP)
        }

        return ReajusteResult(
            amount = amount,
            from = from.date,
            to = to.date,
            ufAtFrom = from.value,
            ufAtTo = to.value,
            ufUnits = units,
            // Rounded once, at the end: converting through UF units and then
            // rounding twice would drift on large amounts.
            adjustedAmount = amount.multiply(factor).setScale(0, RoundingMode.HALF_UP),
            factor = factor.setScale(6, RoundingMode.HALF_UP),
            variationPct = (factor - BigDecimal.ONE).multiply(BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP),
            annualisedPct = annualised,
            days = days,
        )
    }
}
