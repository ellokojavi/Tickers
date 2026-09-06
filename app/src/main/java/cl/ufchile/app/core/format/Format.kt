package cl.ufchile.app.core.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Chilean number and date formatting.
 *
 * es-CL uses "." for thousands and "," for decimals, and the peso is normally
 * written without decimals while the UF is written with two. The formats are
 * built explicitly instead of relying on the platform locale, so the output is
 * identical on every device regardless of the user's system settings.
 */
object Fmt {
    val LOCALE: Locale = Locale.forLanguageTag("es-CL")

    private val symbols = DecimalFormatSymbols(LOCALE).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private fun df(pattern: String) = DecimalFormat(pattern, symbols)

    private val pesos = df("#,##0")
    private val pesosDec = df("#,##0.00")
    private val uf = df("#,##0.00")
    private val uf4 = df("#,##0.0000")
    private val pct = df("#,##0.00")
    private val pct1 = df("#,##0.0")

    /** "$40.880" */
    fun clp(v: BigDecimal): String = "$" + pesos.format(v.setScale(0, RoundingMode.HALF_UP))

    /** "$40.880,36" — used where the cents actually matter. */
    fun clpExact(v: BigDecimal): String = "$" + pesosDec.format(v)

    /** "1.234,56 UF" */
    fun uf(v: BigDecimal): String = uf.format(v) + " UF"

    /** "1.234,5678 UF" — payment tables. */
    fun uf4(v: BigDecimal): String = uf4.format(v) + " UF"

    /** "7,1567x" — a multiplicative adjustment factor. */
    fun factor(v: BigDecimal): String = uf4.format(v) + "x"

    /** "13.396" — a plain count, grouped. Any figure shown to the user gets
     *  its separators, whether or not today's data happens to reach a
     *  thousand. */
    fun integer(v: Long): String = pesos.format(v)

    fun integer(v: Int): String = pesos.format(v)

    /** "36,7" — one decimal, for spans expressed in months or years. */
    fun decimal1(v: Double): String = pct1.format(v).removeSuffix("%")

    /** "+3,25%" */
    fun pctSigned(v: BigDecimal): String {
        val s = pct.format(v.abs())
        return when {
            v.signum() > 0 -> "+$s%"
            v.signum() < 0 -> "-$s%"
            else -> "0,00%"
        }
    }

    fun pct(v: BigDecimal): String = pct.format(v) + "%"
    fun pct1(v: BigDecimal): String = pct1.format(v) + "%"

    /** "+$1,32" */
    fun clpSigned(v: BigDecimal): String {
        val s = pesosDec.format(v.abs())
        return when {
            v.signum() > 0 -> "+$$s"
            v.signum() < 0 -> "-$$s"
            else -> "$0,00"
        }
    }

    // ------------------------------------------------------------- dates

    private val dayMonthYear = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", LOCALE)
    private val shortDate = DateTimeFormatter.ofPattern("dd-MM-yyyy", LOCALE)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE)
    private val monthYearShort = DateTimeFormatter.ofPattern("MMM yyyy", LOCALE)

    fun longDate(d: LocalDate): String = dayMonthYear.format(d).replaceFirstChar { it.uppercase() }
    fun shortDate(d: LocalDate): String = shortDate.format(d)
    fun monthYear(m: YearMonth): String = monthYear.format(m).replaceFirstChar { it.uppercase() }
    fun monthYearShort(m: YearMonth): String =
        monthYearShort.format(m).replaceFirstChar { it.uppercase() }.removeSuffix(".")

    /** "hoy", "ayer", "hace 3 días" — for the freshness indicator. */
    fun relativeDay(d: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(d, today)
        return when {
            days == 0L -> "hoy"
            days == 1L -> "ayer"
            days > 1L -> "hace $days días"
            days == -1L -> "mañana"
            else -> "en ${-days} días"
        }
    }

    /** Parses user input that may contain "." separators and a "," decimal. */
    fun parseNumber(text: String): BigDecimal? {
        val cleaned = text.trim()
            .replace("$", "")
            .replace("UF", "", ignoreCase = true)
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".")
        if (cleaned.isBlank()) return null
        return cleaned.toBigDecimalOrNull()
    }
}
