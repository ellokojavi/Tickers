import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { money, type Money } from "../money.ts";
import { annualisedPct, chartSummary, clpToUf, delta, deltaPct, ufToClp } from "../ufEngine.ts";
import { convert } from "../ufReajuste.ts";
import * as Fmt from "../../core/format.ts";

/**
 * The conversion, re-adjustment and formatting half of the parity contract.
 * `shared/golden/uf.json` is read by this test and by
 * app/src/test/java/cl/tickers/app/parity/UfGoldenVectorTest.kt, and both must
 * agree with it exactly. See shared/PARITY.md.
 *
 * Formatting is in here because a thousands separator or a month name that
 * differs between the channels is as visible as a wrong figure, and the two
 * implementations get there by completely different routes: ICU on Android and
 * hand-rolled grouping here.
 */

interface Case { expected: string }
interface Golden {
  ufToClp: (Case & { uf: string; rate: string })[];
  clpToUf: (Case & { clp: string; rate: string })[];
  delta: (Case & { from: string; to: string })[];
  deltaPct: (Case & { from: string; to: string })[];
  annualisedPct: (Case & { from: string; to: string; days: number })[];
  chartSummary: {
    from: string; to: string; days: number;
    expected: { periodPct: string; annualisedPct: string | null };
  }[];
  reajuste: {
    amount: string;
    from: { date: string; value: string };
    to: { date: string; value: string };
    expected: Record<string, string | number>;
  }[];
  format: Record<string, { v: string | number; expected: string }[]>;
}

const golden: Golden = JSON.parse(
  readFileSync(new URL("../../../../shared/golden/uf.json", import.meta.url), "utf8"),
) as Golden;

/** Every expectation carries its own label so a failure names the input. */
const check = (label: string, actual: string, expected: string) =>
  expect(`${label} -> ${actual}`).toBe(`${label} -> ${expected}`);

describe("uf conversions", () => {
  it("match the shared fixture", () => {
    for (const c of golden.ufToClp) {
      check(`ufToClp(${c.uf}, ${c.rate})`, ufToClp(money(c.uf), money(c.rate)).toFixed(0), c.expected);
    }
    for (const c of golden.clpToUf) {
      check(`clpToUf(${c.clp}, ${c.rate})`, clpToUf(money(c.clp), money(c.rate)).toFixed(4), c.expected);
    }
    for (const c of golden.delta) {
      check(`delta(${c.from}, ${c.to})`, delta(money(c.from), money(c.to)).toFixed(4), c.expected);
    }
    for (const c of golden.deltaPct) {
      check(`deltaPct(${c.from}, ${c.to})`, deltaPct(money(c.from), money(c.to)).toFixed(2), c.expected);
    }
    for (const c of golden.annualisedPct) {
      check(
        `annualisedPct(${c.from}, ${c.to}, ${c.days})`,
        annualisedPct(money(c.from), money(c.to), c.days).toFixed(2),
        c.expected,
      );
    }
  });
});

/**
 * The line above every history chart. In the fixture because whether a window
 * is annualised at all is a design decision, and the two channels disagreeing
 * about it would show as one chart saying more than the other.
 */
describe("chart summary", () => {
  it("matches the shared fixture", () => {
    for (const c of golden.chartSummary) {
      const label = `chartSummary(${c.from}, ${c.to}, ${c.days})`;
      const s = chartSummary(money(c.from), money(c.to), c.days);
      check(`${label} periodPct`, s.periodPct.toFixed(2), c.expected.periodPct);
      check(
        `${label} annualisedPct`,
        s.annualisedPct?.toFixed(2) ?? "null",
        c.expected.annualisedPct ?? "null",
      );
    }
  });
});

describe("reajuste", () => {
  it("matches the shared fixture", () => {
    for (const c of golden.reajuste) {
      const r = convert(
        money(c.amount),
        { date: c.from.date, value: money(c.from.value) },
        { date: c.to.date, value: money(c.to.value) },
      );
      const label = `reajuste ${c.amount} ${c.from.date}->${c.to.date}`;
      check(`${label} ufUnits`, r.ufUnits.toFixed(4), String(c.expected.ufUnits));
      check(`${label} adjustedAmount`, r.adjustedAmount.toFixed(0), String(c.expected.adjustedAmount));
      check(`${label} factor`, r.factor.toFixed(6), String(c.expected.factor));
      check(`${label} variationPct`, r.variationPct.toFixed(2), String(c.expected.variationPct));
      check(`${label} annualisedPct`, r.annualisedPct.toFixed(2), String(c.expected.annualisedPct));
      check(`${label} days`, String(r.days), String(c.expected.days));
    }
  });
});

describe("formatting", () => {
  it("matches the shared fixture", () => {
    const byMoney: Record<string, (v: Money) => string> = {
      clp: Fmt.clp, clpExact: Fmt.clpExact, clpSigned: Fmt.clpSigned,
      uf: Fmt.uf, uf4: Fmt.uf4, factor: Fmt.factor,
      pct: Fmt.pct, pct1: Fmt.pct1, pctSigned: Fmt.pctSigned,
    };
    for (const [name, f] of Object.entries(byMoney)) {
      for (const c of golden.format[name]!) {
        check(`${name}(${String(c.v)})`, f(money(String(c.v))), c.expected);
      }
    }

    for (const c of golden.format.integer!) check(`integer(${String(c.v)})`, Fmt.integer(Number(c.v)), c.expected);
    for (const c of golden.format.period!) check(`period(${String(c.v)})`, Fmt.period(Number(c.v)), c.expected);

    const byDate: Record<string, (d: string) => string> = {
      longDate: Fmt.longDate, shortDate: Fmt.shortDate,
      dayMonth: Fmt.dayMonth, monthYearShort: Fmt.monthYearShort,
    };
    for (const [name, f] of Object.entries(byDate)) {
      for (const c of golden.format[name]!) {
        check(`${name}(${String(c.v)})`, f(String(c.v)), c.expected);
      }
    }
  });
});
