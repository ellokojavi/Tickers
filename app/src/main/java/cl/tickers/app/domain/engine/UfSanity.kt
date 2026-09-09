package cl.tickers.app.domain.engine

import cl.tickers.app.domain.model.DatedValue
import java.time.temporal.ChronoUnit

/**
 * Rejects values that cannot be real.
 *
 * This is not defensive paranoia: the public source has served 608,15 for
 * 2014-12-29 when the actual UF was about 24.627, and that value would
 * otherwise have been stored and charted as fact. The same feed serves the
 * current year, so the guard runs on every sync, not just on the bundled data.
 *
 * The largest genuine day-over-day change in the whole 49-year series is
 * 0,2633%, so the threshold sits roughly four times above anything real.
 * Rejected days become gaps, which the app already reports honestly, rather
 * than being interpolated into an invented official figure.
 */
object UfSanity {

    /** Maximum believable change per elapsed day, as a fraction. */
    const val MAX_DAILY_CHANGE = 0.01

    /**
     * @param values incoming values, in any order.
     * @param anchor the last value already known to be good, if any, so the
     *   first value of a batch is checked too.
     * @return the values that survive, in date order.
     */
    fun filter(values: List<DatedValue>, anchor: DatedValue? = null): List<DatedValue> {
        if (values.isEmpty()) return values
        val sorted = values.sortedBy { it.date }
        val out = ArrayList<DatedValue>(sorted.size)
        var previous = anchor

        for (candidate in sorted) {
            if (candidate.value.signum() <= 0) continue
            val last = previous
            if (last != null) {
                val gap = ChronoUnit.DAYS.between(last.date, candidate.date)
                    .coerceAtLeast(1L)
                val change = (candidate.value.toDouble() / last.value.toDouble()) - 1.0
                if (kotlin.math.abs(change) > MAX_DAILY_CHANGE * gap) continue
            }
            out += candidate
            previous = candidate
        }
        return out
    }
}
