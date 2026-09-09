package cl.ufchile.app.parity

import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.domain.engine.UfEngine
import cl.ufchile.app.domain.engine.UfReajuste
import cl.ufchile.app.domain.model.UfValue
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * The conversion, re-adjustment and formatting half of the parity contract.
 * `shared/golden/uf.json` is read by this test and by
 * web/src/domain/__tests__/goldenVectors.test.ts, and both must agree with it
 * exactly. See shared/PARITY.md.
 *
 * Formatting is in here because a thousands separator or a month name that
 * differs between the channels is as visible as a wrong figure, and the two
 * implementations get there by completely different routes: ICU on Android and
 * hand-rolled grouping on the web.
 */
class UfGoldenVectorTest {

    private val golden: JsonObject = Json
        .parseToJsonElement(File("../shared/golden/uf.json").readText())
        .jsonObject

    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
    private fun JsonObject.dec(key: String): BigDecimal = BigDecimal(str(key))
    private fun section(name: String): JsonArray = golden[name]!!.jsonArray
    private fun formatSection(name: String): JsonArray =
        golden["format"]!!.jsonObject[name]!!.jsonArray

    private fun BigDecimal.at(scale: Int): String =
        setScale(scale, RoundingMode.HALF_UP).toPlainString()

    /** Every expectation carries its own label so a failure names the input. */
    private fun check(label: String, actual: String, expected: String) {
        assertThat("$label -> $actual").isEqualTo("$label -> $expected")
    }

    @Test
    fun `uf conversions match the shared fixture`() {
        for (e in section("ufToClp")) {
            val c = e.jsonObject
            check(
                "ufToClp(${c.str("uf")}, ${c.str("rate")})",
                UfEngine.ufToClp(c.dec("uf"), c.dec("rate")).at(0),
                c.str("expected"),
            )
        }
        for (e in section("clpToUf")) {
            val c = e.jsonObject
            check(
                "clpToUf(${c.str("clp")}, ${c.str("rate")})",
                UfEngine.clpToUf(c.dec("clp"), c.dec("rate")).at(4),
                c.str("expected"),
            )
        }
        for (e in section("delta")) {
            val c = e.jsonObject
            check(
                "delta(${c.str("from")}, ${c.str("to")})",
                UfEngine.delta(c.dec("from"), c.dec("to")).at(4),
                c.str("expected"),
            )
        }
        for (e in section("deltaPct")) {
            val c = e.jsonObject
            check(
                "deltaPct(${c.str("from")}, ${c.str("to")})",
                UfEngine.deltaPct(c.dec("from"), c.dec("to")).at(2),
                c.str("expected"),
            )
        }
        for (e in section("annualisedPct")) {
            val c = e.jsonObject
            val days = c.str("days").toLong()
            check(
                "annualisedPct(${c.str("from")}, ${c.str("to")}, $days)",
                UfEngine.annualisedPct(c.dec("from"), c.dec("to"), days).at(2),
                c.str("expected"),
            )
        }
    }

    @Test
    fun `reajuste matches the shared fixture`() {
        for (e in section("reajuste")) {
            val c = e.jsonObject
            val from = c["from"]!!.jsonObject
            val to = c["to"]!!.jsonObject
            val result = UfReajuste.convert(
                c.dec("amount"),
                UfValue(LocalDate.parse(from.str("date")), from.dec("value")),
                UfValue(LocalDate.parse(to.str("date")), to.dec("value")),
            )
            val expected = c["expected"]!!.jsonObject
            val label = "reajuste ${c.str("amount")} ${from.str("date")}->${to.str("date")}"

            check("$label ufUnits", result.ufUnits.at(4), expected.str("ufUnits"))
            check("$label adjustedAmount", result.adjustedAmount.at(0), expected.str("adjustedAmount"))
            check("$label factor", result.factor.at(6), expected.str("factor"))
            check("$label variationPct", result.variationPct.at(2), expected.str("variationPct"))
            check("$label annualisedPct", result.annualisedPct.at(2), expected.str("annualisedPct"))
            check("$label days", result.days.toString(), expected.str("days"))
        }
    }

    @Test
    fun `formatting matches the shared fixture`() {
        fun money(name: String, f: (BigDecimal) -> String) {
            for (e in formatSection(name)) {
                val c = e.jsonObject
                check("$name(${c.str("v")})", f(c.dec("v")), c.str("expected"))
            }
        }
        money("clp", Fmt::clp)
        money("clpExact", Fmt::clpExact)
        money("clpSigned", Fmt::clpSigned)
        money("uf", Fmt::uf)
        money("uf4", Fmt::uf4)
        money("factor", Fmt::factor)
        money("pct", Fmt::pct)
        money("pct1", Fmt::pct1)
        money("pctSigned", Fmt::pctSigned)

        for (e in formatSection("integer")) {
            val c = e.jsonObject
            check("integer(${c.str("v")})", Fmt.integer(c.str("v").toLong()), c.str("expected"))
        }
        for (e in formatSection("period")) {
            val c = e.jsonObject
            check("period(${c.str("v")})", Fmt.period(c.str("v").toLong()), c.str("expected"))
        }

        fun date(name: String, f: (LocalDate) -> String) {
            for (e in formatSection(name)) {
                val c = e.jsonObject
                check("$name(${c.str("v")})", f(LocalDate.parse(c.str("v"))), c.str("expected"))
            }
        }
        date("longDate", Fmt::longDate)
        date("shortDate", Fmt::shortDate)
        date("dayMonth", Fmt::dayMonth)
        for (e in formatSection("monthYearShort")) {
            val c = e.jsonObject
            val d = LocalDate.parse(c.str("v"))
            check(
                "monthYearShort(${c.str("v")})",
                Fmt.monthYearShort(YearMonth.of(d.year, d.month)),
                c.str("expected"),
            )
        }
    }
}
