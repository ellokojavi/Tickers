package cl.ufchile.app.data.remote

import cl.ufchile.app.domain.model.DataSource
import cl.ufchile.app.domain.model.Indicator
import cl.ufchile.app.domain.model.UfValue
import java.time.LocalDate

/** What a UF provider must be able to answer, regardless of which one it is. */
interface UfRemoteDataSource {
    val source: DataSource

    /** True when this source is usable at all (e.g. an API key is configured). */
    val available: Boolean

    /**
     * Every UF value published for [year], including days in the future that
     * have already been published.
     */
    suspend fun ufForYear(year: Int): List<UfValue>

    /** Companion indicators. Empty when the source does not provide them. */
    suspend fun indicators(): List<Indicator> = emptyList()
}

class CmfDataSource(
    private val api: CmfApi,
    private val apiKey: String,
) : UfRemoteDataSource {
    override val source = DataSource.CMF
    override val available: Boolean get() = apiKey.isNotBlank()

    override suspend fun ufForYear(year: Int): List<UfValue> {
        check(available) { "CMF API key no configurada" }
        return api.year(year, apiKey).ufs
            .mapNotNull { runCatching { UfValue(it.date(), it.amount()) }.getOrNull() }
            .sortedBy { it.date }
    }
}

class MindicadorDataSource(
    private val api: MindicadorApi,
) : UfRemoteDataSource {
    override val source = DataSource.MINDICADOR
    override val available = true

    override suspend fun ufForYear(year: Int): List<UfValue> =
        api.seriesForYear("uf", year).serie
            .mapNotNull { runCatching { UfValue(it.date(), it.amount()) }.getOrNull() }
            .sortedBy { it.date }

    override suspend fun indicators(): List<Indicator> {
        val l = api.latest()
        return listOfNotNull(l.ivp, l.dolar, l.euro, l.utm, l.ipc).map {
            Indicator(
                code = it.codigo,
                name = it.nombre,
                unit = it.unidad,
                date = runCatching { it.date() }.getOrDefault(LocalDate.now()),
                value = it.amount(),
            )
        }
    }
}
