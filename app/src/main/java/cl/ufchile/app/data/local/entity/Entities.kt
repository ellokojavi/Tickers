package cl.ufchile.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "uf_values")
data class UfValueEntity(
    @PrimaryKey val date: LocalDate,
    val value: BigDecimal,
    val source: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "indicators")
data class IndicatorEntity(
    @PrimaryKey val code: String,
    val name: String,
    val unit: String,
    val date: LocalDate,
    val value: BigDecimal,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "simulations")
data class SimulationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val notes: String,
    val propertyValueUf: BigDecimal,
    val downPaymentUf: BigDecimal,
    val annualRatePct: BigDecimal,
    val termYears: Int,
    val rateConvention: String,
    val lifeInsuranceMonthlyPct: BigDecimal,
    val fireInsuranceMonthlyUf: BigDecimal,
    val originationFeeUf: BigDecimal,
    val stampTaxPct: BigDecimal,
    val otherUpfrontCostsUf: BigDecimal,
    val startDate: LocalDate,
    /** Prepayments serialised as JSON; a rarely-used list is not worth a table. */
    val prepaymentsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
