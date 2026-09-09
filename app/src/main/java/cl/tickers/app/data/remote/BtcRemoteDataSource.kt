package cl.tickers.app.data.remote

import cl.tickers.app.domain.engine.Horizon
import cl.tickers.app.domain.engine.NetworkStatus
import cl.tickers.app.domain.engine.SourceFailure
import cl.tickers.app.domain.engine.candlesNeeded
import cl.tickers.app.domain.engine.chartPlan
import cl.tickers.app.domain.engine.classifyFetchError
import cl.tickers.app.domain.engine.FetchErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.math.BigDecimal
import java.net.SocketException
import java.net.UnknownHostException
import java.time.Instant
import javax.net.ssl.SSLException

/**
 * Bitcoin prices from free, keyless public endpoints, tried in order until one
 * answers.
 *
 * Binance is in this chain and not in the web app's: it sends no CORS header,
 * so a browser cannot read it, while a phone can. Buda would give BTC/CLP
 * directly and is the Chilean reference, but it sends no CORS header either and
 * so is left out of both rather than making the channels disagree about where a
 * price came from.
 *
 * Prices are parsed straight from their string form into BigDecimal. Going
 * through a Double first would round the number before this app ever saw it.
 */
class BtcRemoteDataSource(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private data class Source(val name: String, val url: String, val parse: (String) -> String)

    private val sources = listOf(
        Source("Coinbase", "https://api.coinbase.com/v2/prices/BTC-USD/spot") { body ->
            json.parseToJsonElement(body).jsonObject["data"]!!
                .jsonObject["amount"]!!.jsonPrimitive.content
        },
        Source(
            "CoinGecko",
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd",
        ) { body ->
            json.parseToJsonElement(body).jsonObject["bitcoin"]!!
                .jsonObject["usd"]!!.jsonPrimitive.content
        },
        Source("Kraken", "https://api.kraken.com/0/public/Ticker?pair=XBTUSD") { body ->
            json.parseToJsonElement(body).jsonObject["result"]!!.jsonObject
                .values.first().jsonObject["c"]!!.jsonArray[0].jsonPrimitive.content
        },
        Source("Binance", "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT") { body ->
            json.parseToJsonElement(body).jsonObject["price"]!!.jsonPrimitive.content
        },
    )

    /** True when the request never reached a server, as opposed to answering unusably. */
    private fun isTransport(e: Exception): Boolean =
        e is UnknownHostException || e is SocketException ||
            e is InterruptedIOException || e is SSLException

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "tickers-app").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body!!.string()
        }
    }

    suspend fun spot(network: NetworkStatus): BtcSpotResult = withContext(Dispatchers.IO) {
        val failures = mutableListOf<SourceFailure>()
        for (source in sources) {
            try {
                val raw = source.parse(get(source.url))
                val usd = BigDecimal(raw)
                if (usd.signum() <= 0) throw Exception("precio inválido $raw")
                return@withContext BtcSpotResult.Ok(usd, source.name, System.currentTimeMillis())
            } catch (e: Exception) {
                failures += SourceFailure(
                    source.name,
                    e.message ?: e.javaClass.simpleName,
                    isTransport(e),
                )
            }
        }
        BtcSpotResult.Failed(
            classifyFetchError(network, failures),
            failures,
            System.currentTimeMillis(),
        )
    }

    /**
     * Closing prices over one span, oldest first, from Coinbase Exchange's
     * public candle API. Free, keyless.
     *
     * A span needing more than the per-request cap is fetched in consecutive
     * windows; the plan thins anything past a year so the point count stays in
     * the range a chart can actually draw.
     */
    suspend fun candles(h: Horizon): List<BtcCandle> = withContext(Dispatchers.IO) {
        val plan = chartPlan(h)
        val stepMs = plan.granularitySec * 1000L
        val now = System.currentTimeMillis()
        val from = now - candlesNeeded(h) * stepMs
        val chunkMs = MAX_PER_REQUEST * stepMs

        val byTime = sortedMapOf<Long, BigDecimal>()
        var start = from
        while (start < now) {
            val end = minOf(start + chunkMs, now)
            val url = "$CANDLES_URL?granularity=${plan.granularitySec}" +
                "&start=${Instant.ofEpochMilli(start)}&end=${Instant.ofEpochMilli(end)}"
            for (row in json.parseToJsonElement(get(url)).jsonArray) {
                // [ time, low, high, open, close, volume ]
                val cells = row.jsonArray
                if (cells.size < 5) continue
                val t = cells[0].jsonPrimitive.content.toLongOrNull() ?: continue
                byTime[t * 1000L] = BigDecimal(cells[4].jsonPrimitive.content)
            }
            start = end
        }

        var points = byTime.entries
            .filter { it.key >= from }
            .map { BtcCandle(it.key, it.value) }
        if (plan.keepEvery > 1) {
            // Thinned from the newest end so the last point is always the latest candle.
            points = points.reversed()
                .filterIndexed { i, _ -> i % plan.keepEvery == 0 }
                .reversed()
        }
        points
    }

    private companion object {
        /** The exchange caps one request at this many candles. */
        const val MAX_PER_REQUEST = 300L
        const val CANDLES_URL = "https://api.exchange.coinbase.com/products/BTC-USD/candles"
    }
}

data class BtcCandle(val at: Long, val usd: BigDecimal)

sealed interface BtcSpotResult {
    data class Ok(val usd: BigDecimal, val source: String, val at: Long) : BtcSpotResult
    data class Failed(
        val kind: FetchErrorKind,
        val failures: List<SourceFailure>,
        val at: Long,
    ) : BtcSpotResult
}
