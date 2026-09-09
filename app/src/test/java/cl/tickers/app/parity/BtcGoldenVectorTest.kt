package cl.tickers.app.parity

import cl.tickers.app.core.format.Fmt
import cl.tickers.app.domain.engine.BtcEngine
import cl.tickers.app.domain.engine.FetchErrorKind
import cl.tickers.app.domain.engine.Horizon
import cl.tickers.app.domain.engine.NetworkStatus
import cl.tickers.app.domain.engine.SourceFailure
import cl.tickers.app.domain.engine.candlesNeeded
import cl.tickers.app.domain.engine.chartPlan
import cl.tickers.app.domain.engine.classifyFetchError
import cl.tickers.app.domain.engine.stampKind
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The bitcoin half of the parity contract. `shared/golden/btc.json` is read by
 * this test and by web/src/domain/__tests__/btcGoldenVectors.test.ts, and both
 * must agree with it exactly. See shared/PARITY.md.
 *
 * The chart plan is in here because it is a design decision, not an
 * implementation detail: which candle width each span uses decides how the
 * chart reads and how many requests it costs, and the two channels drawing the
 * same span at different resolutions would be a real difference nobody would
 * think to look for.
 */
class BtcGoldenVectorTest {

    private val golden: JsonObject = Json
        .parseToJsonElement(File("../shared/golden/btc.json").readText())
        .jsonObject

    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
    private fun JsonObject.dec(key: String): BigDecimal = BigDecimal(str(key))
    private fun section(name: String): JsonArray = golden[name]!!.jsonArray

    private fun BigDecimal.at(scale: Int): String =
        setScale(scale, RoundingMode.HALF_UP).toPlainString()

    /** Every expectation carries its own label so a failure names the input. */
    private fun check(label: String, actual: String, expected: String) {
        assertThat("$label -> $actual").isEqualTo("$label -> $expected")
    }

    @Test
    fun `conversions match the shared fixture`() {
        for (e in section("btcUsdToClp")) {
            val c = e.jsonObject
            check(
                "btcUsdToClp(${c.str("btcUsd")}, ${c.str("usdClp")})",
                BtcEngine.btcUsdToClp(c.dec("btcUsd"), c.dec("usdClp")).at(0),
                c.str("expected"),
            )
        }
        for (e in section("btcToClp")) {
            val c = e.jsonObject
            check(
                "btcToClp(${c.str("btc")})",
                BtcEngine.btcToClp(c.dec("btc"), c.dec("btcUsd"), c.dec("usdClp")).at(0),
                c.str("expected"),
            )
        }
        for (e in section("clpToBtc")) {
            val c = e.jsonObject
            check(
                "clpToBtc(${c.str("clp")})",
                BtcEngine.clpToBtc(c.dec("clp"), c.dec("btcUsd"), c.dec("usdClp")).at(BtcEngine.BTC_SCALE),
                c.str("expected"),
            )
        }
        for (e in section("btcUsdToUf")) {
            val c = e.jsonObject
            check(
                "btcUsdToUf(${c.str("btcUsd")}, ${c.str("usdClp")}, ${c.str("uf")})",
                BtcEngine.btcUsdToUf(c.dec("btcUsd"), c.dec("usdClp"), c.dec("uf")).at(BtcEngine.UF_SCALE),
                c.str("expected"),
            )
        }
    }

    @Test
    fun `the chart plan matches the shared fixture`() {
        val seen = mutableSetOf<Horizon>()
        for (e in section("chartPlan")) {
            val c = e.jsonObject
            val h = Horizon.valueOf(c.str("horizon"))
            seen += h
            val plan = chartPlan(h)
            check("$h granularitySec", plan.granularitySec.toString(), c.str("granularitySec"))
            check("$h keepEvery", plan.keepEvery.toString(), c.str("keepEvery"))
            check("$h refreshMs", plan.refreshMs.toString(), c.str("refreshMs"))
            check("$h candlesNeeded", candlesNeeded(h).toString(), c.str("candlesNeeded"))
        }
        // A horizon added on one channel and not the other would otherwise pass
        // by simply not being in the fixture.
        assertThat(seen).containsExactlyElementsIn(Horizon.entries)
    }

    @Test
    fun `chart label granularity and the formatters match the shared fixture`() {
        for (e in section("stampKind")) {
            val c = e.jsonObject
            val h = Horizon.valueOf(c.str("horizon"))
            check("stampKind($h)", stampKind(h).name.lowercase(), c.str("expected"))
        }

        val format = golden["format"]!!.jsonObject
        for (e in format["usd"]!!.jsonArray) {
            val c = e.jsonObject
            check("usd(${c.str("v")})", Fmt.usd(c.dec("v")), c.str("expected"))
        }
        // These straddle Chile's daylight saving change and local midnight,
        // which is where two implementations quietly stop agreeing.
        for ((name, f) in listOf<Pair<String, (Long) -> String>>(
            "timeHm" to Fmt::timeHm,
            "dayMonthNum" to Fmt::dayMonthNum,
            "monthYearOf" to Fmt::monthYearOf,
        )) {
            for (e in format[name]!!.jsonArray) {
                val c = e.jsonObject
                val millis = c.str("v").toLong()
                check("$name($millis)", f(millis), c.str("expected"))
            }
        }
    }

    @Test
    fun `error classification and its wording match the shared fixture`() {
        for (e in section("classify")) {
            val c = e.jsonObject
            val failures = c["failures"]!!.jsonArray.map {
                val f = it.jsonObject
                SourceFailure(f.str("source"), f.str("message"), f["transport"]!!.jsonPrimitive.boolean)
            }
            val kind = classifyFetchError(NetworkStatus.valueOf(c.str("network")), failures)
            check(c.str("name"), kind.name, c.str("expected"))
            check("${c.str("name")} isConnectivity", kind.isConnectivity.toString(), c.str("isConnectivity"))
        }

        val seen = mutableSetOf<FetchErrorKind>()
        for (e in section("copy")) {
            val c = e.jsonObject
            val kind = FetchErrorKind.valueOf(c.str("kind"))
            seen += kind
            check("${kind.name} headline", kind.headline, c.str("headline"))
            check("${kind.name} hint", kind.hint, c.str("hint"))
        }
        assertThat(seen).containsExactlyElementsIn(FetchErrorKind.entries)
    }
}
