package cl.ufchile.app.data.seed

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.YearMonth

@Serializable
data class UfSeedFile(
    val source: String = "",
    val description: String = "",
    val generated: String = "",
    val anchors: Map<String, Double> = emptyMap(),
)

/**
 * The UF value on the 9th of every month since August 1977, shipped inside the
 * APK. It is what makes the inflation calculator work on first launch with no
 * network, and it is the fallback whenever the API is unreachable.
 *
 * Regenerate with tools/build_uf_seed.py.
 */
object UfSeed {
    private const val ASSET = "uf_monthly_seed.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: Map<YearMonth, BigDecimal>? = null

    fun anchors(context: Context): Map<YearMonth, BigDecimal> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context).also { cached = it }
        }
    }

    private fun load(context: Context): Map<YearMonth, BigDecimal> {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val file = json.decodeFromString(UfSeedFile.serializer(), text)
        return file.anchors.entries.mapNotNull { (key, value) ->
            runCatching {
                val (y, m) = key.split("-")
                YearMonth.of(y.toInt(), m.toInt()) to BigDecimal.valueOf(value)
            }.getOrNull()
        }.toMap()
    }
}
