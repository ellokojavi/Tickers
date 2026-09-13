import { useEffect, useState } from "preact/hooks";
import { fetchCandles, fetchSpot, type Candle, type Spot, type SpotFailure } from "./btcApi.ts";
import type { Horizon } from "../domain/btc.ts";

/**
 * Bitcoin for the overview card: the price, and enough of a series to draw one
 * line under it.
 *
 * The bitcoin screen keeps its own copy of this because it owns a horizon the
 * reader can change, and the two are never on screen at once. This one is
 * fixed at the span that screen opens on, so moving from the card to the
 * screen shows the same shape rather than a different one.
 *
 * This is the app's only unprompted network call: the UF and the dollar are
 * already on the device, but bitcoin is a market price and there is no
 * bundling it. The card therefore has to render before the answer arrives, and
 * has to say so when it never does.
 */
export const GLANCE_HORIZON: Horizon = "D30";

/** The same cadence the bitcoin screen polls on, and the published limit. */
const POLL_MS = 30_000;

export interface BtcGlance {
  readonly spot: Spot | SpotFailure | null;
  readonly candles: readonly Candle[];
  readonly loading: boolean;
}

export const useBtcGlance = (): BtcGlance => {
  const [spot, setSpot] = useState<Spot | SpotFailure | null>(null);
  const [candles, setCandles] = useState<readonly Candle[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let live = true;
    const read = () => { void fetchSpot().then((s) => { if (live) setSpot(s); }); };
    read();
    const id = window.setInterval(read, POLL_MS);
    return () => { live = false; window.clearInterval(id); };
  }, []);

  // Once, on arrival. A thirty-day line does not change shape while someone
  // reads it, and the screen that owns the series is one tap away.
  useEffect(() => {
    let live = true;
    void fetchCandles(GLANCE_HORIZON)
      .then((c) => { if (live) setCandles(c); })
      .catch(() => { /* the card says so; the price line above it is separate */ })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, []);

  return { spot, candles, loading };
};
