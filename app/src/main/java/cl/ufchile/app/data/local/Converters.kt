package cl.ufchile.app.data.local

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Money is stored as text, never as REAL. A binary float would silently lose
 * the exact cent values the whole app is built on.
 */
class Converters {
    @TypeConverter fun dateToString(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun stringToDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
    @TypeConverter fun decimalToString(v: BigDecimal?): String? = v?.toPlainString()
    @TypeConverter fun stringToDecimal(v: String?): BigDecimal? = v?.let(::BigDecimal)
}
