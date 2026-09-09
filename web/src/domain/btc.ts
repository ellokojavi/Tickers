import { scale, type Money } from "./money.ts";
import { usdToClp } from "./fx.ts";

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
 * Money stays Money here for the same reason it does everywhere else in this
 * app. The Android app this logic came from used Double; a satoshi is 1e-8 of a
 * bitcoin and eight decimals of a five-figure price is exactly where float64
 * starts lying.
 */

/** A bitcoin has eight decimals, and the last one is a satoshi. */
export const BTC_SCALE = 8;
export const UF_SCALE = 4;

/** One bitcoin in pesos, from its dollar price and the dollar's peso value. */
export const btcUsdToClp = (btcUsd: Money, usdClp: Money): Money => usdToClp(btcUsd, usdClp);

/** [btc] bitcoins in pesos. Rounded once, at the end. */
export const btcToClp = (btc: Money, btcUsd: Money, usdClp: Money): Money =>
  scale(btc.times(btcUsd).times(usdClp), 0);

/** Pesos in bitcoin, to the satoshi. */
export const clpToBtc = (clp: Money, btcUsd: Money, usdClp: Money): Money =>
  scale(clp.div(btcUsd.times(usdClp)), BTC_SCALE);

/**
 * One bitcoin in UF: what it is worth in the unit Chilean prices are actually
 * written in, which is the only way to see it net of local inflation.
 */
export const btcUsdToUf = (btcUsd: Money, usdClp: Money, ufValue: Money): Money =>
  scale(btcUsd.times(usdClp).div(ufValue), UF_SCALE);

// ------------------------------------------------------------------- chart

export const HORIZONS = ["H1", "D1", "D7", "D30", "Y1", "Y5"] as const;
export type Horizon = (typeof HORIZONS)[number];

/** Written the way the UF and dollar ranges are: "1A", not "1a". */
export const horizonLabel: Record<Horizon, string> = {
  H1: "1H", D1: "24H", D7: "7D", D30: "30D", Y1: "1A", Y5: "5A",
};

const MINUTE = 60_000;
export const horizonMillis: Record<Horizon, number> = {
  H1: 60 * MINUTE,
  D1: 24 * 60 * MINUTE,
  D7: 7 * 24 * 60 * MINUTE,
  D30: 30 * 24 * 60 * MINUTE,
  Y1: 365 * 24 * 60 * MINUTE,
  Y5: 1826 * 24 * 60 * MINUTE,
};

export interface ChartPlan {
  /** Candle width in seconds, from the exchange's fixed set. */
  readonly granularitySec: number;
  /** Keep one candle in this many, for spans too long to request candle by candle. */
  readonly keepEvery: number;
  /** How long a fetched series stays good. */
  readonly refreshMs: number;
}

/**
 * Granularity per span, chosen so every chart lands at roughly 150-300 points:
 * dense enough to read as a curve, sparse enough to stay cheap. The exchange
 * caps a request at 300 candles, so a span needing more is fetched in windows
 * and, past a year, thinned.
 */
export const chartPlan = (h: Horizon): ChartPlan => {
  switch (h) {
    case "H1": return { granularitySec: 60, keepEvery: 1, refreshMs: MINUTE };
    case "D1": return { granularitySec: 300, keepEvery: 1, refreshMs: 5 * MINUTE };
    case "D7": return { granularitySec: 3600, keepEvery: 1, refreshMs: 60 * MINUTE };
    case "D30": return { granularitySec: 21600, keepEvery: 1, refreshMs: 6 * 60 * MINUTE };
    case "Y1": return { granularitySec: 86400, keepEvery: 1, refreshMs: 24 * 60 * MINUTE };
    case "Y5": return { granularitySec: 86400, keepEvery: 7, refreshMs: 24 * 60 * MINUTE };
  }
};

/**
 * How precise a chart label has to be for a span to be readable. The domain
 * picks the granularity; the formatting layer knows how to write one. An hour
 * of trading labelled by date says nothing, and five years labelled by minute
 * says too much.
 */
export type StampKind = "time" | "day" | "month";

export const stampKind = (h: Horizon): StampKind => {
  switch (h) {
    case "H1": case "D1": return "time";
    case "D7": case "D30": return "day";
    case "Y1": case "Y5": return "month";
  }
};

/** Points a span needs at its granularity, before thinning. Drives the windowing. */
export const candlesNeeded = (h: Horizon): number =>
  Math.ceil(horizonMillis[h] / (chartPlan(h).granularitySec * 1000));

// ------------------------------------------------------------------ errors

/** The device's network state when a fetch was attempted. */
export type NetworkStatus = "NONE" | "UNVALIDATED" | "ONLINE";

/**
 * One source's failure. `transport` separates "the request never got an answer"
 * (DNS, routing, timeout, TLS) from "we got an answer we could not use" (an HTTP
 * status, bad JSON). Those mean very different things to the person waiting.
 */
export interface SourceFailure {
  readonly source: string;
  readonly message: string;
  readonly transport: boolean;
}

export type FetchErrorKind = "OFFLINE" | "NO_INTERNET" | "UNREACHABLE" | "SOURCE";

/** Phrased for the screen, not for a stack trace. */
export const fetchErrorCopy: Record<FetchErrorKind, { headline: string; hint: string }> = {
  OFFLINE: {
    headline: "Sin conexión",
    hint: "Revisa el wifi o los datos móviles. El precio se actualiza solo al volver.",
  },
  NO_INTERNET: {
    headline: "La red no llega a internet",
    hint: "Este wifi puede necesitar que inicies sesión.",
  },
  UNREACHABLE: {
    headline: "No se pudo llegar a las fuentes de precio",
    hint: "Tu conexión está activa, así que probablemente sea pasajero.",
  },
  SOURCE: {
    headline: "Las fuentes de precio respondieron con error",
    hint: "No hay nada que hacer; la app sigue reintentando.",
  },
};

/** True when the cause is this device's connection rather than the exchanges. */
export const isConnectivity = (k: FetchErrorKind): boolean =>
  k === "OFFLINE" || k === "NO_INTERNET";

/**
 * Device connectivity wins over whatever the sources said: with no network,
 * "sin conexión" is the whole story and the failures underneath it are noise.
 */
export const classifyFetchError = (
  network: NetworkStatus,
  failures: readonly SourceFailure[],
): FetchErrorKind => {
  if (network === "NONE") return "OFFLINE";
  if (network === "UNVALIDATED") return "NO_INTERNET";
  // Every source failed before getting a reply. Four exchanges being down at
  // once is far less likely than a connection that is up but not carrying
  // traffic, so that is what this says.
  if (failures.length === 0 || failures.every((f) => f.transport)) return "UNREACHABLE";
  return "SOURCE";
};
