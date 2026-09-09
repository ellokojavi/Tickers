package cl.tickers.app.parity

import cl.tickers.app.domain.engine.ConverterEngine
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The three-field converters, which are the one place in the app where the same
 * quantity is shown three ways at once and so the one place where a rounding
 * mistake shows itself as two different answers on one screen.
 *
 * `shared/golden/converter.json` is read by this test and by
 * web/src/domain/__tests__/converterGoldenVectors.test.ts. See shared/PARITY.md.
 */
class ConverterGoldenVectorTest {

    private val golden: JsonObject = Json
        .parseToJsonElement(File("../shared/golden/converter.json").readText())
        .jsonObject

    private val rates = golden["rates"]!!.jsonObject
    private val uf = BigDecimal(rates["uf"]!!.jsonPrimitive.content)
    private val usdClp = BigDecimal(rates["usdClp"]!!.jsonPrimitive.content)
    private val btcUsd = BigDecimal(rates["btcUsd"]!!.jsonPrimitive.content)

    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
    private fun BigDecimal.at(scale: Int): String =
        setScale(scale, RoundingMode.HALF_UP).toPlainString()

    private fun check(label: String, actual: String?, expected: String?) {
        assertThat("$label -> $actual").isEqualTo("$label -> $expected")
    }

    @Test
    fun `the uf converter matches the shared fixture`() {
        for (e in golden["uf"]!!.jsonArray) {
            val c = e.jsonObject
            val dollar = c["usdClp"]!!.let {
                if (it is JsonNull) null else BigDecimal(it.jsonPrimitive.content)
            }
            val field = ConverterEngine.UfField.valueOf(c.str("field").uppercase())
            val result = ConverterEngine.ufConvert(field, BigDecimal(c.str("amount")), uf, dollar)
            val expected = c["expected"]!!
            val label = "ufConvert(${c.str("field")}, ${c.str("amount")}, dólar=$dollar)"

            if (expected is JsonNull) {
                assertThat(result).isNull()
                continue
            }
            val exp = expected.jsonObject
            check("$label uf", result!!.uf.at(4), exp.str("uf"))
            check("$label clp", result.clp.at(0), exp.str("clp"))
            val expUsd = exp["usd"]!!
            check(
                "$label usd",
                result.usd?.at(2),
                if (expUsd is JsonNull) null else expUsd.jsonPrimitive.content,
            )
        }
    }

    @Test
    fun `the bitcoin converter matches the shared fixture`() {
        for (e in golden["btc"]!!.jsonArray) {
            val c = e.jsonObject
            val field = ConverterEngine.BtcField.valueOf(c.str("field").uppercase())
            val result = ConverterEngine.btcConvert(
                field, BigDecimal(c.str("amount")), btcUsd, usdClp,
            )!!
            val exp = c["expected"]!!.jsonObject
            val label = "btcConvert(${c.str("field")}, ${c.str("amount")})"
            check("$label btc", result.btc.at(8), exp.str("btc"))
            check("$label usd", result.usd.at(2), exp.str("usd"))
            check("$label clp", result.clp.at(0), exp.str("clp"))
        }
    }
}
