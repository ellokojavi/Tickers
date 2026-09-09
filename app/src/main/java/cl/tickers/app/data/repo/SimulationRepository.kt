package cl.tickers.app.data.repo

import cl.tickers.app.data.local.dao.SimulationDao
import cl.tickers.app.data.local.entity.SimulationEntity
import cl.tickers.app.domain.model.MortgageInput
import cl.tickers.app.domain.model.Prepayment
import cl.tickers.app.domain.model.PrepaymentMode
import cl.tickers.app.domain.model.RateConvention
import cl.tickers.app.domain.model.Simulation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal

@Serializable
private data class PrepaymentDto(val month: Int, val amount: String, val mode: String)

/** CRUD over saved simulations. */
class SimulationRepository(private val dao: SimulationDao) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<Simulation>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun byId(id: Long): Simulation? = dao.byId(id)?.toDomain()

    suspend fun create(name: String, notes: String, input: MortgageInput): Long {
        val now = System.currentTimeMillis()
        return dao.insert(toEntity(0L, name, notes, input, now, now))
    }

    suspend fun update(simulation: Simulation) {
        dao.update(
            toEntity(
                simulation.id,
                simulation.name,
                simulation.notes,
                simulation.input,
                simulation.createdAt,
                System.currentTimeMillis(),
            )
        )
    }

    /** Saves a copy under a new name, leaving the original untouched. */
    suspend fun duplicate(id: Long, newName: String): Long? {
        val original = byId(id) ?: return null
        return create(newName, original.notes, original.input)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    // ------------------------------------------------------------- mapping

    private fun toEntity(
        id: Long,
        name: String,
        notes: String,
        i: MortgageInput,
        createdAt: Long,
        updatedAt: Long,
    ) = SimulationEntity(
        id = id,
        name = name,
        notes = notes,
        propertyValueUf = i.propertyValueUf,
        downPaymentUf = i.downPaymentUf,
        annualRatePct = i.annualRatePct,
        termYears = i.termYears,
        rateConvention = i.rateConvention.name,
        lifeInsuranceMonthlyPct = i.lifeInsuranceMonthlyPct,
        fireInsuranceMonthlyUf = i.fireInsuranceMonthlyUf,
        originationFeeUf = i.originationFeeUf,
        stampTaxPct = i.stampTaxPct,
        otherUpfrontCostsUf = i.otherUpfrontCostsUf,
        firstPaymentDate = i.firstPaymentDate,
        prepaymentsJson = json.encodeToString(
            ListSerializer(PrepaymentDto.serializer()),
            i.prepayments.map { PrepaymentDto(it.monthNumber, it.amountUf.toPlainString(), it.mode.name) },
        ),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SimulationEntity.toDomain(): Simulation {
        val prepayments = runCatching {
            json.decodeFromString(ListSerializer(PrepaymentDto.serializer()), prepaymentsJson)
                .map {
                    Prepayment(
                        monthNumber = it.month,
                        amountUf = BigDecimal(it.amount),
                        mode = runCatching { PrepaymentMode.valueOf(it.mode) }
                            .getOrDefault(PrepaymentMode.REDUCE_TERM),
                    )
                }
        }.getOrDefault(emptyList())

        return Simulation(
            id = id,
            name = name,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            input = MortgageInput(
                propertyValueUf = propertyValueUf,
                downPaymentUf = downPaymentUf,
                annualRatePct = annualRatePct,
                termYears = termYears,
                rateConvention = runCatching { RateConvention.valueOf(rateConvention) }
                    .getOrDefault(RateConvention.NOMINAL_DIVIDED),
                lifeInsuranceMonthlyPct = lifeInsuranceMonthlyPct,
                fireInsuranceMonthlyUf = fireInsuranceMonthlyUf,
                originationFeeUf = originationFeeUf,
                stampTaxPct = stampTaxPct,
                otherUpfrontCostsUf = otherUpfrontCostsUf,
                firstPaymentDate = firstPaymentDate,
                prepayments = prepayments,
            ),
        )
    }
}
