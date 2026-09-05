package cl.ufchile.app.domain.engine

import cl.ufchile.app.domain.model.InflationResult
import cl.ufchile.app.domain.model.UfEquivalence
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.YearMonth

/**
 * Converts amounts between two points in time using a chained price index.
 *
 * ## Why the index is derived from the UF
 *
 * The UF is re-adjusted daily so that its value on the 9th of month M+1 divided
 * by its value on the 9th of month M equals exactly (1 + CPI variation of month
 * M-1). The UF is published with two decimals on a value in the tens of
 * thousands, so the implied index carries roughly 1e-7 relative precision.
 *
 * Chaining the officially published CPI variations instead would compound a
 * rounding error of up to 0.05 pp per month across 400+ months. Deriving the
 * index from the UF avoids that entirely, and the source is still official.
 *
 * The mapping is therefore: index(month m) = UF value on the 9th of month m+2.
 *
 * Verified against the published CPI for every month of 2024 with a maximum
 * deviation of 0.000 pp — see InflationEngineTest.
 */
class InflationEngine(
    /** UF value on the 9th of each month, keyed by that month. */
    private val anchors: Map<YearMonth, BigDecimal>,
) {
    companion object {
        /** The UF series only supports a continuous index from this month on. */
        val EARLIEST: YearMonth = YearMonth.of(1977, 8)
        private val MC = MathContext.DECIMAL64
        private const val ANCHOR_LAG_MONTHS = 2L
    }

    /** Oldest month that can be used as an origin. */
    val earliestMonth: YearMonth
        get() = anchors.keys.minOrNull()?.minusMonths(ANCHOR_LAG_MONTHS) ?: EARLIEST

    /** Newest month with a settled price level. */
    val latestMonth: YearMonth
        get() = anchors.keys.maxOrNull()?.minusMonths(ANCHOR_LAG_MONTHS) ?: EARLIEST

    fun supports(month: YearMonth): Boolean =
        !month.isBefore(earliestMonth) && !month.isAfter(latestMonth)

    /** Price level of [month], on an arbitrary but internally consistent base. */
    fun index(month: YearMonth): BigDecimal? =
        anchors[month.plusMonths(ANCHOR_LAG_MONTHS)]

    /**
     * Restates [amount] from the prices of [from] into the prices of [to].
     *
     * @throws IllegalArgumentException when either month lies outside coverage.
     */
    fun convert(amount: BigDecimal, from: YearMonth, to: YearMonth): InflationResult {
        val i0 = index(from) ?: error(from)
        val i1 = index(to) ?: error(to)

        val factor = i1.divide(i0, MC)
        val months = ((to.year - from.year) * 12 + (to.monthValue - from.monthValue))
        val cumulativePct = (factor - BigDecimal.ONE).multiply(BigDecimal(100))

        // Constant annual rate that produces the same factor over the period.
        val annualizedPct = if (months <= 0) BigDecimal.ZERO else {
            val years = months.toDouble() / 12.0
            val a = Math.pow(factor.toDouble(), 1.0 / years) - 1.0
            BigDecimal(a * 100).setScale(2, RoundingMode.HALF_UP)
        }

        return InflationResult(
            amount = amount,
            from = from,
            to = to,
            adjustedAmount = amount.multiply(factor).setScale(0, RoundingMode.HALF_UP),
            factor = factor.setScale(6, RoundingMode.HALF_UP),
            cumulativePct = cumulativePct.setScale(2, RoundingMode.HALF_UP),
            annualizedPct = annualizedPct,
            months = months,
        )
    }

    /**
     * Expresses [amount] as UF units at [ufAtOrigin] and converts those units
     * back to pesos at [ufAtTarget]. This is the second, independent reading
     * the UI shows next to the CPI-based one.
     */
    fun asUfEquivalence(
        amount: BigDecimal,
        ufAtOrigin: BigDecimal,
        ufAtTarget: BigDecimal,
    ): UfEquivalence {
        val units = amount.divide(ufAtOrigin, 4, RoundingMode.HALF_UP)
        return UfEquivalence(
            ufUnits = units,
            ufValueAtOrigin = ufAtOrigin,
            ufValueAtTarget = ufAtTarget,
            adjustedAmount = units.multiply(ufAtTarget).setScale(0, RoundingMode.HALF_UP),
        )
    }

    /** Month-over-month CPI variation implied by the UF, in percent. */
    fun monthlyVariationPct(month: YearMonth): BigDecimal? {
        val prev = index(month.minusMonths(1)) ?: return null
        val cur = index(month) ?: return null
        return (cur.divide(prev, MC) - BigDecimal.ONE)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)
    }

    private fun error(month: YearMonth): Nothing = throw IllegalArgumentException(
        "Sin datos de IPC para $month. Cobertura: $earliestMonth a $latestMonth."
    )
}
