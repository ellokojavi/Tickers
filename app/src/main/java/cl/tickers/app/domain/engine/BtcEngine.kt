package cl.tickers.app.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Bitcoin, as a Chilean sees it.
 *
 * The market prices BTC in dollars, so that is what the sources return and what
 * this app treats as the real figure. Pesos are derived: BTC/USD times the
 * dólar observado. Those are different kinds of number — a price that moves by
 * the second times a rate the Banco Central publishes once a business day — so
 * the peso figure is always shown with the day of the dollar it used, and never
 * pretends to a precision it does not have.
 *
 * Money stays BigDecimal here for the same reason it does everywhere else in
 * this app. The alert app this logic came from used Double; a satoshi is 1e-8
 * of a bitcoin and eight decimals of a five-figure price is exactly where a
 * Double starts lying.
 *
 * The TypeScript twin is web/src/domain/btc.ts, pinned to the same golden
 * vectors. See shared/PARITY.md.
 */
object BtcEngine {

    /** A bitcoin has eight decimals, and the last one is a satoshi. */
    const val BTC_SCALE = 8
    const val UF_SCALE = 4

    /** Room to divide before the single rounding at the end. */
    private const val WORKING_SCALE = 30

    /** One bitcoin in pesos, from its dollar price and the dollar's peso value. */
    fun btcUsdToClp(btcUsd: BigDecimal, usdClp: BigDecimal): BigDecimal =
        btcUsd.multiply(usdClp).setScale(0, RoundingMode.HALF_UP)

    /** [btc] bitcoins in pesos. Rounded once, at the end. */
    fun btcToClp(btc: BigDecimal, btcUsd: BigDecimal, usdClp: BigDecimal): BigDecimal =
        btc.multiply(btcUsd).multiply(usdClp).setScale(0, RoundingMode.HALF_UP)

    /** Pesos in bitcoin, to the satoshi. */
    fun clpToBtc(clp: BigDecimal, btcUsd: BigDecimal, usdClp: BigDecimal): BigDecimal =
        clp.divide(btcUsd.multiply(usdClp), BTC_SCALE, RoundingMode.HALF_UP)

    /**
     * One bitcoin in UF: what it is worth in the unit Chilean prices are
     * actually written in, which is the only way to see it net of local
     * inflation.
     */
    fun btcUsdToUf(btcUsd: BigDecimal, usdClp: BigDecimal, ufValue: BigDecimal): BigDecimal =
        btcUsd.multiply(usdClp)
            .divide(ufValue, WORKING_SCALE, RoundingMode.HALF_UP)
            .setScale(UF_SCALE, RoundingMode.HALF_UP)
}

// --------------------------------------------------------------------- chart

private const val MINUTE = 60_000L

enum class Horizon(val label: String, val millis: Long) {
    H1("1h", 60 * MINUTE),
    D1("24h", 24 * 60 * MINUTE),
    D7("7d", 7 * 24 * 60 * MINUTE),
    D30("30d", 30 * 24 * 60 * MINUTE),
    Y1("1a", 365L * 24 * 60 * MINUTE),
    Y5("5a", 1826L * 24 * 60 * MINUTE),
}

data class ChartPlan(
    /** Candle width in seconds, from the exchange's fixed set. */
    val granularitySec: Long,
    /** Keep one candle in this many, for spans too long to request candle by candle. */
    val keepEvery: Int,
    /** How long a fetched series stays good. */
    val refreshMs: Long,
)

/**
 * Granularity per span, chosen so every chart lands at roughly 150-300 points:
 * dense enough to read as a curve, sparse enough to stay cheap. The exchange
 * caps a request at 300 candles, so a span needing more is fetched in windows
 * and, past a year, thinned.
 */
fun chartPlan(h: Horizon): ChartPlan = when (h) {
    Horizon.H1 -> ChartPlan(60, 1, MINUTE)
    Horizon.D1 -> ChartPlan(300, 1, 5 * MINUTE)
    Horizon.D7 -> ChartPlan(3600, 1, 60 * MINUTE)
    Horizon.D30 -> ChartPlan(21600, 1, 6 * 60 * MINUTE)
    Horizon.Y1 -> ChartPlan(86400, 1, 24 * 60 * MINUTE)
    Horizon.Y5 -> ChartPlan(86400, 7, 24 * 60 * MINUTE)
}

/** Points a span needs at its granularity, before thinning. Drives the windowing. */
fun candlesNeeded(h: Horizon): Long {
    val step = chartPlan(h).granularitySec * 1000L
    return (h.millis + step - 1) / step
}

// -------------------------------------------------------------------- errors

/** The device's network state when a fetch was attempted. */
enum class NetworkStatus { NONE, UNVALIDATED, ONLINE }

/**
 * One source's failure. [transport] separates "the request never got an answer"
 * (DNS, routing, timeout, TLS) from "we got an answer we could not use" (an HTTP
 * status, bad JSON). Those mean very different things to the person waiting.
 */
data class SourceFailure(val source: String, val message: String, val transport: Boolean)

/** Phrased for the screen, not for a stack trace. */
enum class FetchErrorKind(val headline: String, val hint: String) {
    OFFLINE(
        "Sin conexión",
        "Revisa el wifi o los datos móviles. El precio se actualiza solo al volver.",
    ),
    NO_INTERNET(
        "La red no llega a internet",
        "Este wifi puede necesitar que inicies sesión.",
    ),
    UNREACHABLE(
        "No se pudo llegar a las fuentes de precio",
        "Tu conexión está activa, así que probablemente sea pasajero.",
    ),
    SOURCE(
        "Las fuentes de precio respondieron con error",
        "No hay nada que hacer; la app sigue reintentando.",
    );

    /** True when the cause is this device's connection rather than the exchanges. */
    val isConnectivity: Boolean get() = this == OFFLINE || this == NO_INTERNET
}

/**
 * Device connectivity wins over whatever the sources said: with no network,
 * "sin conexión" is the whole story and the failures underneath it are noise.
 */
fun classifyFetchError(network: NetworkStatus, failures: List<SourceFailure>): FetchErrorKind =
    when {
        network == NetworkStatus.NONE -> FetchErrorKind.OFFLINE
        network == NetworkStatus.UNVALIDATED -> FetchErrorKind.NO_INTERNET
        // Every source failed before getting a reply. Four exchanges being down
        // at once is far less likely than a connection that is up but not
        // carrying traffic, so that is what this says.
        failures.isEmpty() || failures.all { it.transport } -> FetchErrorKind.UNREACHABLE
        else -> FetchErrorKind.SOURCE
    }

/**
 * How precise a chart label has to be for a span to be readable. The domain
 * picks the granularity; the formatting layer knows how to write one. An hour
 * of trading labelled by date says nothing, and five years labelled by minute
 * says too much.
 */
enum class StampKind { TIME, DAY, MONTH }

fun stampKind(h: Horizon): StampKind = when (h) {
    Horizon.H1, Horizon.D1 -> StampKind.TIME
    Horizon.D7, Horizon.D30 -> StampKind.DAY
    Horizon.Y1, Horizon.Y5 -> StampKind.MONTH
}
