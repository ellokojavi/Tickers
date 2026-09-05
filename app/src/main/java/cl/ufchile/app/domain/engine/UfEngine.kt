package cl.ufchile.app.domain.engine

import cl.ufchile.app.domain.model.UfValue
import java.math.BigDecimal
import java.math.RoundingMode

/** Conversions and deltas over the UF series. */
object UfEngine {

    fun clpToUf(clp: BigDecimal, ufValue: BigDecimal): BigDecimal =
        if (ufValue.signum() == 0) BigDecimal.ZERO
        else clp.divide(ufValue, 4, RoundingMode.HALF_UP)

    fun ufToClp(uf: BigDecimal, ufValue: BigDecimal): BigDecimal =
        uf.multiply(ufValue).setScale(0, RoundingMode.HALF_UP)

    /** Absolute change between two UF values. */
    fun delta(from: BigDecimal, to: BigDecimal): BigDecimal = to - from

    /** Percentage change between two UF values. */
    fun deltaPct(from: BigDecimal, to: BigDecimal): BigDecimal =
        if (from.signum() == 0) BigDecimal.ZERO
        else (to - from).divide(from, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)

    /**
     * Constant annual rate that reproduces the change from [from] to [to] over
     * [days]. Short windows produce large figures; that is arithmetic, not a
     * bug, and is why the UI labels it "anualizado".
     */
    fun annualisedPct(from: BigDecimal, to: BigDecimal, days: Long): BigDecimal {
        if (days <= 0L || from.signum() <= 0 || to.signum() <= 0) return BigDecimal.ZERO
        val years = days.toDouble() / 365.25
        val factor = to.toDouble() / from.toDouble()
        val annual = (Math.pow(factor, 1.0 / years) - 1.0) * 100.0
        if (annual.isNaN() || annual.isInfinite()) return BigDecimal.ZERO
        return BigDecimal(annual).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * The last value in [series] that is not in the future.
     * The series may legitimately contain published future dates.
     */
    fun currentOf(series: List<UfValue>, today: java.time.LocalDate): UfValue? =
        series.filter { !it.date.isAfter(today) }.maxByOrNull { it.date }

    /** Published values that lie beyond [today]. */
    fun futureOf(series: List<UfValue>, today: java.time.LocalDate): List<UfValue> =
        series.filter { it.date.isAfter(today) }.sortedBy { it.date }
}
