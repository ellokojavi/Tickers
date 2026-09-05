package cl.ufchile.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cl.ufchile.app.data.local.AppDatabase
import cl.ufchile.app.data.local.dao.UfDao
import cl.ufchile.app.data.local.entity.UfValueEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class UfDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UfDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.ufDao()
    }

    @After
    fun tearDown() = db.close()

    private fun uf(date: String, value: String) =
        UfValueEntity(LocalDate.parse(date), BigDecimal(value), "TEST")

    @Test
    fun `upsert replaces rather than duplicates a date`() = runTest {
        dao.upsertAll(listOf(uf("2026-09-05", "40880.36")))
        dao.upsertAll(listOf(uf("2026-09-05", "40880.99")))

        assertThat(dao.count()).isEqualTo(1)
        assertThat(dao.byDate(LocalDate.of(2026, 9, 5))!!.value)
            .isEqualTo(BigDecimal("40880.99"))
    }

    @Test
    fun `atOrBefore falls back to the nearest earlier day`() = runTest {
        dao.upsertAll(
            listOf(
                uf("2026-09-01", "40875.09"),
                uf("2026-09-05", "40880.36"),
            )
        )

        // A date with no row of its own resolves backwards, never forwards.
        val found = dao.atOrBefore(LocalDate.of(2026, 9, 3))
        assertThat(found!!.date).isEqualTo(LocalDate.of(2026, 9, 1))

        assertThat(dao.atOrBefore(LocalDate.of(2020, 1, 1))).isNull()
    }

    @Test
    fun `monthAnchors returns only the ninth of each month`() = runTest {
        dao.upsertAll(
            listOf(
                uf("2026-08-09", "40800.00"),
                uf("2026-08-10", "40801.00"),
                uf("2026-09-09", "40885.63"),
                uf("2026-09-19", "40900.00"),
            )
        )

        val anchors = dao.monthAnchors()

        assertThat(anchors).hasSize(2)
        assertThat(anchors.map { it.date.dayOfMonth }).containsExactly(9, 9)
    }

    @Test
    fun `yearsPresent reports the cached years`() = runTest {
        dao.upsertAll(
            listOf(
                uf("2024-05-09", "1"),
                uf("2025-05-09", "2"),
                uf("2025-06-09", "3"),
            )
        )

        assertThat(dao.yearsPresent()).containsExactly(2024, 2025)
    }

    @Test
    fun `maxDate sees published future days`() = runTest {
        dao.upsertAll(listOf(uf("2026-09-05", "1"), uf("2026-09-09", "2")))

        assertThat(dao.maxDate()).isEqualTo(LocalDate.of(2026, 9, 9))
    }
}
