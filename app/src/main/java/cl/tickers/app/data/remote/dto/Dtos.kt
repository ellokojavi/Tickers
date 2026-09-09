package cl.tickers.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate

// ------------------------------------------------------------- mindicador.cl

@Serializable
data class MindicadorPoint(
    val fecha: String,
    val valor: Double,
) {
    /** The API returns an ISO instant; only the calendar day is meaningful. */
    fun date(): LocalDate = LocalDate.parse(fecha.substring(0, 10))

    // BigDecimal.valueOf goes through Double.toString, so a two-decimal value
    // round-trips exactly instead of picking up binary float artefacts.
    fun amount(): BigDecimal = BigDecimal.valueOf(valor)
}

@Serializable
data class MindicadorSeries(
    val codigo: String = "",
    val nombre: String = "",
    @SerialName("unidad_medida") val unidad: String = "",
    val serie: List<MindicadorPoint> = emptyList(),
)

@Serializable
data class MindicadorIndicator(
    val codigo: String = "",
    val nombre: String = "",
    @SerialName("unidad_medida") val unidad: String = "",
    val fecha: String = "",
    val valor: Double = 0.0,
) {
    fun date(): LocalDate = LocalDate.parse(fecha.substring(0, 10))
    fun amount(): BigDecimal = BigDecimal.valueOf(valor)
}

@Serializable
data class MindicadorLatest(
    val fecha: String = "",
    val uf: MindicadorIndicator? = null,
    val ivp: MindicadorIndicator? = null,
    val dolar: MindicadorIndicator? = null,
    val euro: MindicadorIndicator? = null,
    val utm: MindicadorIndicator? = null,
    val ipc: MindicadorIndicator? = null,
)

// ------------------------------------------------------------- CMF (official)

@Serializable
data class CmfUfPoint(
    @SerialName("Valor") val valor: String = "",
    @SerialName("Fecha") val fecha: String = "",
) {
    fun date(): LocalDate = LocalDate.parse(fecha)

    /** CMF returns Chilean-formatted numbers: "40.880,36". */
    fun amount(): BigDecimal =
        BigDecimal(valor.replace(".", "").replace(",", "."))
}

@Serializable
data class CmfUfResponse(
    @SerialName("UFs") val ufs: List<CmfUfPoint> = emptyList(),
)
