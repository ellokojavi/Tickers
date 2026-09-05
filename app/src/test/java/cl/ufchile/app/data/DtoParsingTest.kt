package cl.ufchile.app.data

import cl.ufchile.app.data.remote.dto.CmfUfResponse
import cl.ufchile.app.data.remote.dto.MindicadorLatest
import cl.ufchile.app.data.remote.dto.MindicadorSeries
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Contract tests against captured payloads from both providers. If either API
 * changes shape, these fail here rather than silently on a user's phone.
 */
class DtoParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun `parses a CMF response with Chilean number formatting`() {
        val payload = """
            {"UFs":[
              {"Valor":"40.880,36","Fecha":"2026-09-05"},
              {"Valor":"5.712,95","Fecha":"1990-03-09"}
            ]}
        """.trimIndent()

        val response = json.decodeFromString(CmfUfResponse.serializer(), payload)

        assertThat(response.ufs).hasSize(2)
        assertThat(response.ufs[0].amount()).isEqualTo(BigDecimal("40880.36"))
        assertThat(response.ufs[0].date()).isEqualTo(LocalDate.of(2026, 9, 5))
        assertThat(response.ufs[1].amount()).isEqualTo(BigDecimal("5712.95"))
    }

    @Test
    fun `parses a mindicador series and keeps exact cents`() {
        val payload = """
            {"version":"1.7.0","codigo":"uf","nombre":"Unidad de fomento (UF)",
             "unidad_medida":"Pesos",
             "serie":[{"fecha":"2026-09-05T04:00:00.000Z","valor":40880.36}]}
        """.trimIndent()

        val series = json.decodeFromString(MindicadorSeries.serializer(), payload)

        assertThat(series.serie).hasSize(1)
        // BigDecimal.valueOf must not introduce binary float noise.
        assertThat(series.serie[0].amount().toPlainString()).isEqualTo("40880.36")
        assertThat(series.serie[0].date()).isEqualTo(LocalDate.of(2026, 9, 5))
    }

    @Test
    fun `parses the mindicador root document`() {
        val payload = """
            {"version":"1.7.0","fecha":"2026-09-05T16:00:00.000Z",
             "uf":{"codigo":"uf","nombre":"Unidad de fomento (UF)","unidad_medida":"Pesos",
                   "fecha":"2026-09-05T04:00:00.000Z","valor":40880.36},
             "dolar":{"codigo":"dolar","nombre":"Dólar observado","unidad_medida":"Pesos",
                      "fecha":"2026-09-04T04:00:00.000Z","valor":933.47},
             "ipc":{"codigo":"ipc","nombre":"IPC","unidad_medida":"Porcentaje",
                    "fecha":"2025-12-01T03:00:00.000Z","valor":-0.2}}
        """.trimIndent()

        val latest = json.decodeFromString(MindicadorLatest.serializer(), payload)

        assertThat(latest.uf?.amount()).isEqualTo(BigDecimal("40880.36"))
        assertThat(latest.dolar?.amount()).isEqualTo(BigDecimal("933.47"))
        assertThat(latest.ipc?.amount()).isEqualTo(BigDecimal("-0.2"))
        assertThat(latest.euro).isNull()
    }

    @Test
    fun `unknown fields and missing sections do not break parsing`() {
        val payload = """{"version":"9.9.9","algo_nuevo":{"x":1},"serie":[]}"""

        val series = json.decodeFromString(MindicadorSeries.serializer(), payload)

        assertThat(series.serie).isEmpty()
        assertThat(series.codigo).isEmpty()
    }

    @Test
    fun `an empty CMF payload yields no values rather than an error`() {
        val response = json.decodeFromString(CmfUfResponse.serializer(), """{"UFs":[]}""")

        assertThat(response.ufs).isEmpty()
    }
}
