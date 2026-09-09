package cl.tickers.app.data.seed

import android.content.Context
import cl.tickers.app.domain.model.DatedValue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * The complete daily UF series, shipped inside the APK.
 *
 * ~18.000 days from August 1977 compress to about 50 kB, which is far cheaper
 * than fetching years on demand: every chart works offline from first launch,
 * and afterwards the app only ever has to ask for the current year.
 *
 * The file is not JSON. Days are contiguous, so it stores one start date and
 * then one value per line in centavos; an empty line is a day the source has no
 * value for, which is left as a gap rather than interpolated. Parsing is a
 * split plus toInt with no tokeniser, which matters because it runs at startup.
 *
 * Regenerate with tools/build_uf_daily_seed.py.
 */
object UfDailySeed {

    const val UF_ASSET = "uf_daily.txt"

    /**
     * The dollar ships in the same format on purpose. It is published on
     * business days only, and an empty line already means "no value that day",
     * so weekends need no new format and no second parser.
     */
    const val USD_ASSET = "usd_daily.txt"
    private const val START_KEY = "# inicio:"

    @Volatile
    private var cachedAnchors: Map<YearMonth, BigDecimal>? = null

    /**
     * Parses the asset format. Pure, so the parser is tested without a device.
     * Returns an empty list when the header is missing or unreadable rather
     * than throwing into app startup.
     */
    fun parse(lines: List<String>): List<DatedValue> {
        val start = lines.firstOrNull { it.startsWith(START_KEY) }
            ?.removePrefix(START_KEY)?.trim()
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return emptyList()

        val out = ArrayList<DatedValue>(lines.size)
        var day = start
        for (line in lines) {
            if (line.startsWith("#")) continue
            val cents = line.trim()
            if (cents.isNotEmpty()) {
                val value = cents.toIntOrNull()
                if (value != null) {
                    // Centavos to pesos with exactly two decimals, no float step.
                    out += DatedValue(day, BigDecimal.valueOf(value.toLong(), 2))
                }
            }
            day = day.plusDays(1)
        }
        return out
    }

    /**
     * The whole series. Deliberately not cached: it is read once to seed the
     * database and holding ~18.000 objects afterwards would be pure waste.
     */
    fun readAll(context: Context, asset: String = UF_ASSET): List<DatedValue> =
        runCatching {
            context.assets.open(asset).bufferedReader().use { parse(it.readLines()) }
        }.getOrDefault(emptyList())

    /**
     * UF value on the 9th of each month: the CPI index anchors. Small enough to
     * keep in memory, unlike the full series.
     */
    fun anchors(context: Context): Map<YearMonth, BigDecimal> {
        cachedAnchors?.let { return it }
        return synchronized(this) {
            cachedAnchors ?: readAll(context)
                .filter { it.date.dayOfMonth == 9 }
                .associate { YearMonth.from(it.date) to it.value }
                .also { cachedAnchors = it }
        }
    }
}
