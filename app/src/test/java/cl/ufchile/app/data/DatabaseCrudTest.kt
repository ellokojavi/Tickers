package cl.ufchile.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cl.ufchile.app.data.local.AppDatabase
import cl.ufchile.app.data.repo.SimulationRepository
import cl.ufchile.app.domain.model.MortgageInput
import cl.ufchile.app.domain.model.Prepayment
import cl.ufchile.app.domain.model.PrepaymentMode
import cl.ufchile.app.domain.model.RateConvention
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Exercises the real Room schema and the real serialisation of a simulation,
 * on the JVM. This is what catches a bad type converter or a lost field before
 * it reaches a device.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseCrudTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SimulationRepository

    private val input = MortgageInput(
        propertyValueUf = BigDecimal("5000.5"),
        downPaymentUf = BigDecimal("1000.25"),
        annualRatePct = BigDecimal("4.55"),
        termYears = 25,
        rateConvention = RateConvention.EFFECTIVE_EQUIVALENT,
        lifeInsuranceMonthlyPct = BigDecimal("0.03"),
        fireInsuranceMonthlyUf = BigDecimal("0.4"),
        originationFeeUf = BigDecimal("5"),
        stampTaxPct = BigDecimal("0.8"),
        otherUpfrontCostsUf = BigDecimal("30"),
        startDate = LocalDate.of(2026, 3, 15),
        prepayments = listOf(
            Prepayment(12, BigDecimal("500"), PrepaymentMode.REDUCE_TERM),
            Prepayment(24, BigDecimal("250.75"), PrepaymentMode.REDUCE_PAYMENT),
        ),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = SimulationRepository(db.simulationDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a saved simulation round trips without losing precision`() = runTest {
        val id = repo.create("Casa Ñuñoa", "con pie del 20%", input)
        val loaded = repo.byId(id)

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("Casa Ñuñoa")
        assertThat(loaded.notes).isEqualTo("con pie del 20%")
        // Exact decimals survive: money is stored as text, never as REAL.
        assertThat(loaded.input.propertyValueUf).isEqualTo(BigDecimal("5000.5"))
        assertThat(loaded.input.annualRatePct).isEqualTo(BigDecimal("4.55"))
        assertThat(loaded.input.rateConvention).isEqualTo(RateConvention.EFFECTIVE_EQUIVALENT)
        assertThat(loaded.input.startDate).isEqualTo(LocalDate.of(2026, 3, 15))
    }

    @Test
    fun `prepayments survive the JSON round trip`() = runTest {
        val id = repo.create("Con abonos", "", input)
        val loaded = repo.byId(id)!!

        assertThat(loaded.input.prepayments).hasSize(2)
        assertThat(loaded.input.prepayments[0].monthNumber).isEqualTo(12)
        assertThat(loaded.input.prepayments[1].amountUf).isEqualTo(BigDecimal("250.75"))
        assertThat(loaded.input.prepayments[1].mode).isEqualTo(PrepaymentMode.REDUCE_PAYMENT)
    }

    @Test
    fun `update changes the row instead of adding one`() = runTest {
        val id = repo.create("Original", "", input)
        val loaded = repo.byId(id)!!

        repo.update(loaded.copy(name = "Renombrada", input = input.copy(termYears = 30)))

        assertThat(repo.observeAll().first()).hasSize(1)
        val updated = repo.byId(id)!!
        assertThat(updated.name).isEqualTo("Renombrada")
        assertThat(updated.input.termYears).isEqualTo(30)
        assertThat(updated.createdAt).isEqualTo(loaded.createdAt)
        assertThat(updated.updatedAt).isAtLeast(loaded.updatedAt)
    }

    @Test
    fun `duplicate copies the inputs under a new id`() = runTest {
        val id = repo.create("Base", "nota", input)
        val copyId = repo.duplicate(id, "Base (copia)")

        assertThat(copyId).isNotNull()
        assertThat(copyId).isNotEqualTo(id)
        val all = repo.observeAll().first()
        assertThat(all).hasSize(2)
        val copy = repo.byId(copyId!!)!!
        assertThat(copy.name).isEqualTo("Base (copia)")
        assertThat(copy.input.propertyValueUf).isEqualTo(input.propertyValueUf)
        assertThat(copy.input.prepayments).hasSize(2)
    }

    @Test
    fun `delete removes only the targeted simulation`() = runTest {
        val keep = repo.create("Se queda", "", input)
        val drop = repo.create("Se borra", "", input)

        repo.delete(drop)

        val all = repo.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all.first().id).isEqualTo(keep)
        assertThat(repo.byId(drop)).isNull()
    }

    @Test
    fun `duplicating a missing simulation returns null`() = runTest {
        assertThat(repo.duplicate(9999L, "fantasma")).isNull()
    }

    @Test
    fun `the list is ordered by most recently updated`() = runTest {
        val first = repo.create("Primera", "", input)
        Thread.sleep(5)
        repo.create("Segunda", "", input)
        Thread.sleep(5)
        repo.update(repo.byId(first)!!.copy(name = "Primera editada"))

        val names = repo.observeAll().first().map { it.name }
        assertThat(names.first()).isEqualTo("Primera editada")
    }
}
