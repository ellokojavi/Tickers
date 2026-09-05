package cl.ufchile.app.data

import androidx.test.core.app.ApplicationProvider
import cl.ufchile.app.data.seed.UfDailySeed
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

/**
 * Loads the bundled series through the real asset pipeline, which is the path
 * that would break if the file were renamed, excluded from packaging, or
 * truncated.
 */
@RunWith(RobolectricTestRunner::class)
class UfSeedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `the full daily series loads from assets`() {
        val values = UfDailySeed.readAll(context)

        assertThat(values.size).isAtLeast(17_900)
        assertThat(values.first().date).isEqualTo(LocalDate.of(1977, 8, 1))
        assertThat(values.last().date).isAtLeast(LocalDate.of(2026, 9, 1))
    }

    @Test
    fun `dates are strictly increasing and never duplicated`() {
        val dates = UfDailySeed.readAll(context).map { it.date }

        assertThat(dates).isInOrder()
        assertThat(dates.toSet()).hasSize(dates.size)
    }

    @Test
    fun `anchors are derived from the same file`() {
        val anchors = UfDailySeed.anchors(context)

        assertThat(anchors.size).isAtLeast(580)
        assertThat(anchors).containsKey(YearMonth.of(1990, 3))
        assertThat(anchors[YearMonth.of(1990, 3)]!!.toDouble()).isWithin(0.01).of(5712.95)
    }

    @Test
    fun `repeated anchor reads are served from the cache`() {
        assertThat(UfDailySeed.anchors(context)).isSameInstanceAs(UfDailySeed.anchors(context))
    }
}
