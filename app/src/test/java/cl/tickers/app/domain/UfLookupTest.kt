package cl.tickers.app.domain

import cl.tickers.app.domain.engine.LookupResult
import cl.tickers.app.domain.engine.UfLookup
import cl.tickers.app.domain.model.UfValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class UfLookupTest {

    private val today = LocalDate.of(2026, 9, 5)
    private val lastPublished = LocalDate.of(2026, 9, 9)

    private fun uf(date: LocalDate, value: String = "40880.36") =
        UfValue(date, BigDecimal(value))

    /**
     * The regression this class exists for: asking about a date past the
     * published horizon used to answer with the last published day's value and
     * label it official.
     */
    @Test
    fun `a date beyond the horizon is never answered with another day's value`() {
        val result = UfLookup.resolve(
            requested = LocalDate.of(2026, 11, 28),
            exact = null,
            nearestEarlier = uf(lastPublished, "40885.63"),
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isInstanceOf(LookupResult.NotPublishedYet::class.java)
        assertThat((result as LookupResult.NotPublishedYet).lastPublished)
            .isEqualTo(lastPublished)
    }

    @Test
    fun `the day after the horizon is already unpublished`() {
        val result = UfLookup.resolve(
            requested = lastPublished.plusDays(1),
            exact = null,
            nearestEarlier = uf(lastPublished),
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isInstanceOf(LookupResult.NotPublishedYet::class.java)
    }

    @Test
    fun `the horizon itself is a published future value`() {
        val result = UfLookup.resolve(
            requested = lastPublished,
            exact = uf(lastPublished, "40885.63"),
            nearestEarlier = null,
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isEqualTo(
            LookupResult.Exact(uf(lastPublished, "40885.63"), isFuture = true)
        )
    }

    @Test
    fun `today is exact and not future`() {
        val result = UfLookup.resolve(
            requested = today,
            exact = uf(today),
            nearestEarlier = null,
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isEqualTo(LookupResult.Exact(uf(today), isFuture = false))
    }

    @Test
    fun `a past date is exact and not future`() {
        val past = LocalDate.of(2010, 3, 15)
        val result = UfLookup.resolve(
            requested = past,
            exact = uf(past, "21000.00"),
            nearestEarlier = null,
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isEqualTo(LookupResult.Exact(uf(past, "21000.00"), isFuture = false))
    }

    /** A substitute is only ever offered for a date inside coverage. */
    @Test
    fun `a gap inside coverage falls back to the nearest earlier day`() {
        val requested = LocalDate.of(2010, 3, 15)
        val nearest = uf(LocalDate.of(2010, 3, 14), "20999.00")

        val result = UfLookup.resolve(
            requested = requested,
            exact = null,
            nearestEarlier = nearest,
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isEqualTo(LookupResult.Nearest(nearest, requested))
    }

    @Test
    fun `dates before the series start are rejected`() {
        val result = UfLookup.resolve(
            requested = LocalDate.of(1970, 1, 1),
            exact = null,
            nearestEarlier = uf(LocalDate.of(1977, 8, 1)),
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isInstanceOf(LookupResult.BeforeCoverage::class.java)
    }

    @Test
    fun `the first day of the series is inside coverage`() {
        val start = UfLookup.SERIES_START
        val result = UfLookup.resolve(
            requested = start,
            exact = uf(start, "33.00"),
            nearestEarlier = null,
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isInstanceOf(LookupResult.Exact::class.java)
    }

    @Test
    fun `nothing cached and nothing nearby is unavailable, not wrong`() {
        val result = UfLookup.resolve(
            requested = LocalDate.of(1990, 5, 5),
            exact = null,
            nearestEarlier = null,
            lastPublished = lastPublished,
            today = today,
        )

        assertThat(result).isEqualTo(LookupResult.Unavailable)
    }

    /** With no series at all, the horizon is unknown and cannot gate anything. */
    @Test
    fun `an empty cache does not fabricate a horizon`() {
        val result = UfLookup.resolve(
            requested = LocalDate.of(2026, 11, 28),
            exact = null,
            nearestEarlier = null,
            lastPublished = null,
            today = today,
        )

        assertThat(result).isEqualTo(LookupResult.Unavailable)
    }

    @Test
    fun `maxSelectable is the last published day, future included`() {
        val series = listOf(uf(today), uf(lastPublished), uf(LocalDate.of(2026, 9, 1)))

        assertThat(UfLookup.maxSelectable(series)).isEqualTo(lastPublished)
        assertThat(UfLookup.maxSelectable(emptyList())).isNull()
    }
}
