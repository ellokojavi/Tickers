package cl.tickers.app.domain.engine

import cl.tickers.app.domain.model.UfValue
import java.time.LocalDate

/**
 * Outcome of asking "what was (or will be) the UF worth on this date".
 *
 * Modelled as a closed set rather than a nullable value because the wrong
 * answers here are the dangerous ones: silently substituting a neighbouring
 * day, or presenting an unpublished date as official.
 */
sealed interface LookupResult {

    /** The date has its own published value. */
    data class Exact(val value: UfValue, val isFuture: Boolean) : LookupResult

    /**
     * No value for that exact day, so the closest earlier one is offered —
     * explicitly, as a substitute the user can see.
     */
    data class Nearest(val value: UfValue, val requested: LocalDate) : LookupResult

    /** Beyond the published horizon. There is no value, and none is invented. */
    data class NotPublishedYet(val lastPublished: LocalDate?) : LookupResult

    /** Before the UF series begins. */
    data class BeforeCoverage(val earliest: LocalDate) : LookupResult

    /** Inside coverage but not cached, and it could not be downloaded. */
    data object Unavailable : LookupResult

    data object Loading : LookupResult
}

object UfLookup {

    /**
     * First day of the daily UF series. Earlier dates are outside what any
     * official source publishes day by day.
     */
    val SERIES_START: LocalDate = LocalDate.of(1977, 8, 1)

    /**
     * Classifies a lookup. Pure, so the rules can be tested without a device
     * or a repository.
     *
     * Order matters: coverage bounds are checked before any value is offered,
     * so a date past the horizon can never be answered with a neighbouring
     * day's value.
     */
    fun resolve(
        requested: LocalDate,
        exact: UfValue?,
        nearestEarlier: UfValue?,
        lastPublished: LocalDate?,
        today: LocalDate,
        earliest: LocalDate = SERIES_START,
    ): LookupResult = when {
        requested.isBefore(earliest) -> LookupResult.BeforeCoverage(earliest)

        // The UF is published up to the 9th of next month and no further. A
        // date beyond that simply has no value yet.
        lastPublished != null && requested.isAfter(lastPublished) ->
            LookupResult.NotPublishedYet(lastPublished)

        exact != null -> LookupResult.Exact(exact, isFuture = requested.isAfter(today))

        nearestEarlier != null -> LookupResult.Nearest(nearestEarlier, requested)

        else -> LookupResult.Unavailable
    }

    /** Latest date a user may ask about: the last published day. */
    fun maxSelectable(series: List<UfValue>): LocalDate? =
        series.maxOfOrNull { it.date }
}
