package cl.ufchile.app.data

import androidx.test.core.app.ApplicationProvider
import cl.ufchile.app.data.seed.UfSeed
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * Loads the seed through the real asset pipeline, which is the path that would
 * break if the file were renamed, excluded from packaging, or malformed.
 */
@RunWith(RobolectricTestRunner::class)
class UfSeedTest {

    @Test
    fun `the bundled seed loads from assets`() {
        val anchors = UfSeed.anchors(ApplicationProvider.getApplicationContext())

        assertThat(anchors.size).isAtLeast(580)
        assertThat(anchors).containsKey(YearMonth.of(1990, 3))
        assertThat(anchors[YearMonth.of(1990, 3)]!!.toDouble()).isWithin(0.01).of(5712.95)
    }

    @Test
    fun `repeated reads are served from the cache`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val first = UfSeed.anchors(context)
        val second = UfSeed.anchors(context)

        assertThat(second).isSameInstanceAs(first)
    }
}
