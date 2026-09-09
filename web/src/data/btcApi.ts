import { money, type Money } from "../domain/money.ts";
import {
  candlesNeeded, chartPlan, classifyFetchError,
  type FetchErrorKind, type Horizon, type NetworkStatus, type SourceFailure,
} from "../domain/btc.ts";

/**
 * Bitcoin prices from free, keyless public endpoints, tried in order until one
 * answers.
 *
 * Every source here sends Access-Control-Allow-Origin, which is what lets this
 * page read them with no server of its own — the same property that makes the
 * rest of the app static. Buda would give BTC/CLP directly, and is the Chilean
 * reference, but it sends no CORS header at all and so is unreachable from a
 * browser. Binance is in the Android app's chain and not in this one: it
 * answers 451 to some regions and sends no CORS header when it does.
 */

interface Source {
  readonly name: string;
  readonly url: string;
  readonly parse: (body: unknown) => string;
}

const SOURCES: readonly Source[] = [
  {
    name: "Coinbase",
    url: "https://api.coinbase.com/v2/prices/BTC-USD/spot",
    parse: (b) => String((b as { data: { amount: string } }).data.amount),
  },
  {
    name: "CoinGecko",
    url: "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd",
    parse: (b) => String((b as { bitcoin: { usd: number } }).bitcoin.usd),
  },
  {
    name: "Kraken",
    url: "https://api.kraken.com/0/public/Ticker?pair=XBTUSD",
    parse: (b) => {
      const result = (b as { result: Record<string, { c: string[] }> }).result;
      return String(Object.values(result)[0]!.c[0]);
    },
  },
];

export interface Spot {
  readonly usd: Money;
  readonly source: string;
  readonly at: number;
}

export interface SpotFailure {
  readonly kind: FetchErrorKind;
  readonly failures: readonly SourceFailure[];
  readonly at: number;
}

/**
 * A browser can say it has no network; it cannot tell a captive portal from a
 * working one, so UNVALIDATED never arises here and the shared classifier
 * simply never returns NO_INTERNET on this channel.
 */
const networkStatus = (): NetworkStatus => (navigator.onLine ? "ONLINE" : "NONE");

/** A request that never reached a server, as opposed to one that answered unusably. */
const isTransport = (e: unknown): boolean =>
  e instanceof TypeError || (e instanceof DOMException && e.name === "AbortError");

const TIMEOUT_MS = 10_000;

const getJson = async (url: string, signal?: AbortSignal): Promise<unknown> => {
  const timeout = AbortSignal.timeout(TIMEOUT_MS);
  const combined = signal === undefined ? timeout : AbortSignal.any([signal, timeout]);
  const response = await fetch(url, { signal: combined });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return await response.json();
};

export const fetchSpot = async (signal?: AbortSignal): Promise<Spot | SpotFailure> => {
  const failures: SourceFailure[] = [];
  for (const source of SOURCES) {
    try {
      const raw = source.parse(await getJson(source.url, signal));
      const usd = money(raw);
      if (usd.cmp(0) <= 0) throw new Error(`precio inválido ${raw}`);
      return { usd, source: source.name, at: Date.now() };
    } catch (e) {
      failures.push({
        source: source.name,
        message: e instanceof Error ? e.message : String(e),
        transport: isTransport(e),
      });
    }
  }
  return {
    kind: classifyFetchError(networkStatus(), failures),
    failures,
    at: Date.now(),
  };
};

// ------------------------------------------------------------------- chart

export interface Candle {
  readonly at: number;
  readonly usd: Money;
}

/** The exchange caps one request at this many candles. */
const MAX_PER_REQUEST = 300;
const CANDLES_URL = "https://api.exchange.coinbase.com/products/BTC-USD/candles";

/**
 * Closing prices over one span, oldest first, from Coinbase Exchange's public
 * candle API. Free, keyless, and it sends CORS.
 *
 * A span needing more than the per-request cap is fetched in consecutive
 * windows; the plan thins anything past a year so the point count stays in the
 * range a chart can actually draw.
 */
export const fetchCandles = async (h: Horizon, signal?: AbortSignal): Promise<Candle[]> => {
  const plan = chartPlan(h);
  const stepMs = plan.granularitySec * 1000;
  const now = Date.now();
  const from = now - candlesNeeded(h) * stepMs;

  const byTime = new Map<number, Money>();
  const chunkMs = MAX_PER_REQUEST * stepMs;
  for (let start = from; start < now; start += chunkMs) {
    const end = Math.min(start + chunkMs, now);
    const url = `${CANDLES_URL}?granularity=${plan.granularitySec}` +
      `&start=${new Date(start).toISOString()}&end=${new Date(end).toISOString()}`;
    const rows = (await getJson(url, signal)) as unknown[];
    if (!Array.isArray(rows)) continue;
    for (const row of rows) {
      // [ time, low, high, open, close, volume ]
      if (!Array.isArray(row) || row.length < 5) continue;
      const [t, , , , close] = row as number[];
      if (typeof t !== "number" || typeof close !== "number") continue;
      byTime.set(t * 1000, money(String(close)));
    }
  }

  let points: Candle[] = [...byTime.entries()]
    .filter(([at]) => at >= from)
    .sort((a, b) => a[0] - b[0])
    .map(([at, usd]) => ({ at, usd }));

  if (plan.keepEvery > 1) {
    // Thinned from the newest end so the last point is always the latest candle.
    points = points.reverse().filter((_, i) => i % plan.keepEvery === 0).reverse();
  }
  return points;
};
