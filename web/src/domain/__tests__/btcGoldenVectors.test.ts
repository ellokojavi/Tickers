import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { money } from "../money.ts";
import {
  BTC_SCALE, HORIZONS, UF_SCALE, btcToClp, btcUsdToClp, btcUsdToUf, candlesNeeded,
  chartPlan, classifyFetchError, clpToBtc, fetchErrorCopy, isConnectivity,
  type FetchErrorKind, type Horizon, type NetworkStatus, type SourceFailure,
} from "../btc.ts";

/**
 * The bitcoin half of the parity contract. `shared/golden/btc.json` is read by
 * this test and by app/src/test/java/cl/tickers/app/parity/BtcGoldenVectorTest.kt,
 * and both must agree with it exactly. See shared/PARITY.md.
 *
 * The chart plan is in here because it is a design decision, not an
 * implementation detail: which candle width each span uses decides how the
 * chart reads and how many requests it costs, and the two channels drawing the
 * same span at different resolutions would be a real difference nobody would
 * think to look for.
 */

interface Golden {
  btcUsdToClp: { btcUsd: string; usdClp: string; expected: string }[];
  btcToClp: { btc: string; btcUsd: string; usdClp: string; expected: string }[];
  clpToBtc: { clp: string; btcUsd: string; usdClp: string; expected: string }[];
  btcUsdToUf: { btcUsd: string; usdClp: string; uf: string; expected: string }[];
  chartPlan: {
    horizon: Horizon; granularitySec: number; keepEvery: number;
    refreshMs: number; candlesNeeded: number;
  }[];
  classify: {
    name: string; network: NetworkStatus; failures: SourceFailure[];
    expected: FetchErrorKind; isConnectivity: boolean;
  }[];
  copy: { kind: FetchErrorKind; headline: string; hint: string }[];
}

const golden: Golden = JSON.parse(
  readFileSync(new URL("../../../../shared/golden/btc.json", import.meta.url), "utf8"),
) as Golden;

/** Every expectation carries its own label so a failure names the input. */
const check = (label: string, actual: string, expected: string) =>
  expect(`${label} -> ${actual}`).toBe(`${label} -> ${expected}`);

describe("bitcoin conversions", () => {
  it("match the shared fixture", () => {
    for (const c of golden.btcUsdToClp) {
      check(`btcUsdToClp(${c.btcUsd}, ${c.usdClp})`,
        btcUsdToClp(money(c.btcUsd), money(c.usdClp)).toFixed(0), c.expected);
    }
    for (const c of golden.btcToClp) {
      check(`btcToClp(${c.btc})`,
        btcToClp(money(c.btc), money(c.btcUsd), money(c.usdClp)).toFixed(0), c.expected);
    }
    for (const c of golden.clpToBtc) {
      check(`clpToBtc(${c.clp})`,
        clpToBtc(money(c.clp), money(c.btcUsd), money(c.usdClp)).toFixed(BTC_SCALE), c.expected);
    }
    for (const c of golden.btcUsdToUf) {
      check(`btcUsdToUf(${c.btcUsd}, ${c.usdClp}, ${c.uf})`,
        btcUsdToUf(money(c.btcUsd), money(c.usdClp), money(c.uf)).toFixed(UF_SCALE), c.expected);
    }
  });
});

describe("the chart plan", () => {
  it("matches the shared fixture", () => {
    const seen = new Set<Horizon>();
    for (const c of golden.chartPlan) {
      seen.add(c.horizon);
      const plan = chartPlan(c.horizon);
      check(`${c.horizon} granularitySec`, String(plan.granularitySec), String(c.granularitySec));
      check(`${c.horizon} keepEvery`, String(plan.keepEvery), String(c.keepEvery));
      check(`${c.horizon} refreshMs`, String(plan.refreshMs), String(c.refreshMs));
      check(`${c.horizon} candlesNeeded`, String(candlesNeeded(c.horizon)), String(c.candlesNeeded));
    }
    // A horizon added on one channel and not the other would otherwise pass by
    // simply not being in the fixture.
    expect([...seen].sort()).toEqual([...HORIZONS].sort());
  });
});

describe("error classification", () => {
  it("and its wording match the shared fixture", () => {
    for (const c of golden.classify) {
      const kind = classifyFetchError(c.network, c.failures);
      check(c.name, kind, c.expected);
      check(`${c.name} isConnectivity`, String(isConnectivity(kind)), String(c.isConnectivity));
    }

    const seen = new Set<FetchErrorKind>();
    for (const c of golden.copy) {
      seen.add(c.kind);
      check(`${c.kind} headline`, fetchErrorCopy[c.kind].headline, c.headline);
      check(`${c.kind} hint`, fetchErrorCopy[c.kind].hint, c.hint);
    }
    expect([...seen].sort()).toEqual(Object.keys(fetchErrorCopy).sort());
  });
});
