package cl.tickers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cl.tickers.app.data.local.entity.IndicatorEntity
import cl.tickers.app.data.local.entity.SimulationEntity
import cl.tickers.app.data.local.entity.UfValueEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface UfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<UfValueEntity>)

    /** Used for the bundled seed: anything already fetched from an API wins. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(values: List<UfValueEntity>)

    @Query("SELECT * FROM uf_values ORDER BY date ASC")
    fun observeAll(): Flow<List<UfValueEntity>>

    @Query("SELECT * FROM uf_values WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<UfValueEntity>>

    @Query("SELECT * FROM uf_values WHERE date = :date LIMIT 1")
    suspend fun byDate(date: LocalDate): UfValueEntity?

    /** Nearest value at or before [date] — for dates the series does not cover. */
    @Query("SELECT * FROM uf_values WHERE date <= :date ORDER BY date DESC LIMIT 1")
    suspend fun atOrBefore(date: LocalDate): UfValueEntity?

    @Query("SELECT MAX(date) FROM uf_values")
    suspend fun maxDate(): LocalDate?

    @Query("SELECT COUNT(*) FROM uf_values")
    suspend fun count(): Int

    @Query("SELECT DISTINCT CAST(strftime('%Y', date) AS INTEGER) FROM uf_values")
    suspend fun yearsPresent(): List<Int>

    /** Values on the 9th of a month: the CPI index anchors. */
    @Query("SELECT * FROM uf_values WHERE CAST(strftime('%d', date) AS INTEGER) = 9")
    suspend fun monthAnchors(): List<UfValueEntity>
}

@Dao
interface IndicatorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<IndicatorEntity>)

    @Query("SELECT * FROM indicators ORDER BY code ASC")
    fun observeAll(): Flow<List<IndicatorEntity>>
}

@Dao
interface SimulationDao {
    @Query("SELECT * FROM simulations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SimulationEntity>>

    @Query("SELECT * FROM simulations WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): SimulationEntity?

    @Insert
    suspend fun insert(entity: SimulationEntity): Long

    @Update
    suspend fun update(entity: SimulationEntity)

    @Delete
    suspend fun delete(entity: SimulationEntity)

    @Query("DELETE FROM simulations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
