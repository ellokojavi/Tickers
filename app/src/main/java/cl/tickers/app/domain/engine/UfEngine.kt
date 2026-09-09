package cl.tickers.app.domain.engine

import cl.tickers.app.domain.model.UfValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

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
     * The slice of [series] a chart should draw: the last [months] up to and
     * including [today], never beyond it.
     *
     * The series legitimately runs past today — the UF is published to the 9th
     * of next month — but a historical chart is a record of what has happened.
     * Those days have their own card on the screen. A null [months] means the
     * whole history.
     */
    fun historyWindow(
        series: List<UfValue>,
        months: Long?,
        today: LocalDate = LocalDate.now(),
    ): List<UfValue> {
        val history = series.filter { !it.date.isAfter(today) }
        if (months == null) return history
        val from = today.minusMonths(months)
        return history.filter { !it.date.isBefore(from) }
    }

    /**
     * Evenly thins [values] to at most [maxPoints], always keeping the first
     * and last.
     *
     * The full series is nearly 18.000 days and a chart cannot usefully draw
     * more points than it has pixels; without this, dragging across the "Máx"
     * range would rebuild an 18.000-segment path on every pointer event. The UF
     * moves smoothly, so even sampling is visually indistinguishable.
     */
    fun downsample(values: List<UfValue>, maxPoints: Int): List<UfValue> {
        if (maxPoints < 2 || values.size <= maxPoints) return values
        val step = (values.size - 1).toDouble() / (maxPoints - 1)
        val out = ArrayList<UfValue>(maxPoints)
        for (i in 0 until maxPoints) {
            out += values[Math.round(i * step).toInt().coerceIn(0, values.lastIndex)]
        }
        return out
    }

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
