package cl.tickers.app.data.remote

import cl.tickers.app.data.remote.dto.CmfUfResponse
import cl.tickers.app.data.remote.dto.MindicadorLatest
import cl.tickers.app.data.remote.dto.MindicadorSeries
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Official source. Requires a free API key; quota is 10.000 requests/month. */
interface CmfApi {
    @GET("api-sbifv3/recursos_api/uf")
    suspend fun today(
        @Query("apikey") apiKey: String,
        @Query("formato") format: String = "json",
    ): CmfUfResponse

    @GET("api-sbifv3/recursos_api/uf/{year}")
    suspend fun year(
        @Path("year") year: Int,
        @Query("apikey") apiKey: String,
        @Query("formato") format: String = "json",
    ): CmfUfResponse

    @GET("api-sbifv3/recursos_api/uf/{year}/{month}")
    suspend fun month(
        @Path("year") year: Int,
        @Path("month") month: String,
        @Query("apikey") apiKey: String,
        @Query("formato") format: String = "json",
    ): CmfUfResponse

    /** Values published beyond the given date — how future UF values are read. */
    @GET("api-sbifv3/recursos_api/uf/posteriores/{year}/{month}/dias/{day}")
    suspend fun after(
        @Path("year") year: Int,
        @Path("month") month: String,
        @Path("day") day: String,
        @Query("apikey") apiKey: String,
        @Query("formato") format: String = "json",
    ): CmfUfResponse

    companion object {
        const val BASE_URL = "https://api.cmfchile.cl/"
    }
}

/** Secondary source. No key required; mirrors Banco Central data. */
interface MindicadorApi {
    @GET("api")
    suspend fun latest(): MindicadorLatest

    @GET("api/{code}")
    suspend fun series(@Path("code") code: String): MindicadorSeries

    @GET("api/{code}/{year}")
    suspend fun seriesForYear(
        @Path("code") code: String,
        @Path("year") year: Int,
    ): MindicadorSeries

    companion object {
        const val BASE_URL = "https://mindicador.cl/"
    }
}
