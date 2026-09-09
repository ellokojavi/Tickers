import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { money } from "../money.ts";
import { btcConvert, ufConvert } from "../converter.ts";

/**
 * The three-field converters, which are the one place in the app where the same
 * quantity is shown three ways at once and so the one place where a rounding
 * mistake shows itself as two different answers on one screen.
 *
 * `shared/golden/converter.json` is read by this test and by
 * app/src/test/java/cl/tickers/app/parity/ConverterGoldenVectorTest.kt.
 * See shared/PARITY.md.
 */
interface Golden {
  rates: { uf: string; usdClp: string; btcUsd: string };
  uf: {
    field: "uf" | "clp" | "usd"; amount: string; usdClp: string | null;
    expected: { uf: string; clp: string; usd: string | null } | null;
  }[];
  btc: {
    field: "btc" | "usd" | "clp"; amount: string;
    expected: { btc: string; usd: string; clp: string } | null;
  }[];
}

const golden: Golden = JSON.parse(
  readFileSync(new URL("../../../../shared/golden/converter.json", import.meta.url), "utf8"),
) as Golden;

const check = (label: string, actual: string | null, expected: string | null) =>
  expect(`${label} -> ${actual}`).toBe(`${label} -> ${expected}`);

describe("the uf converter", () => {
  it("matches the shared fixture", () => {
    for (const c of golden.uf) {
      const r = ufConvert(
        c.field, money(c.amount), money(golden.rates.uf),
        c.usdClp === null ? null : money(c.usdClp),
      );
      const label = `ufConvert(${c.field}, ${c.amount}, dólar=${String(c.usdClp)})`;
      if (c.expected === null) { expect(r).toBeNull(); continue; }
      check(`${label} uf`, r!.uf.toFixed(4), c.expected.uf);
      check(`${label} clp`, r!.clp.toFixed(0), c.expected.clp);
      check(`${label} usd`, r!.usd === null ? null : r!.usd.toFixed(2), c.expected.usd);
    }
  });
});

describe("the bitcoin converter", () => {
  it("matches the shared fixture", () => {
    for (const c of golden.btc) {
      const r = btcConvert(
        c.field, money(c.amount), money(golden.rates.btcUsd), money(golden.rates.usdClp),
      )!;
      const label = `btcConvert(${c.field}, ${c.amount})`;
      check(`${label} btc`, r.btc.toFixed(8), c.expected!.btc);
      check(`${label} usd`, r.usd.toFixed(2), c.expected!.usd);
      check(`${label} clp`, r.clp.toFixed(0), c.expected!.clp);
    }
  });
});
