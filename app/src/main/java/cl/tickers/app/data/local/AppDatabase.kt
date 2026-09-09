package cl.tickers.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cl.tickers.app.data.local.dao.IndicatorDao
import cl.tickers.app.data.local.dao.SimulationDao
import cl.tickers.app.data.local.dao.UfDao
import cl.tickers.app.data.local.entity.IndicatorEntity
import cl.tickers.app.data.local.entity.SimulationEntity
import cl.tickers.app.data.local.entity.UfValueEntity

@Database(
    entities = [UfValueEntity::class, IndicatorEntity::class, SimulationEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ufDao(): UfDao
    abstract fun indicatorDao(): IndicatorDao
    abstract fun simulationDao(): SimulationDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tickers.db")
                .build()
    }
}
