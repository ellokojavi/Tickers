import { describe, expect, it } from "vitest";
import { ZERO, money } from "../money.ts";
import { of } from "../dates.ts";
import {
  annualisedPct, clpToUf, currentOf, delta, deltaPct, downsample, futureOf, historyWindow, ufToClp,
} from "../ufEngine.ts";
import { expectWithin, num } from "../testing.ts";

const today = of(2026, 9, 5);
const series = [
  { date: of(2026, 9, 3), value: money("40877.73") },
  { date: of(2026, 9, 4), value: money("40879.04") },
  { date: of(2026, 9, 5), value: money("40880.36") },
  { date: of(2026, 9, 6), value: money("40881.68") },
  { date: of(2026, 9, 9), value: money("40885.63") },
];

describe("uf engine", () => {
  it("current value ignores published future dates", () => {
    const current = currentOf(series, today);

    expect(current).not.toBeNull();
    expect(current!.date).toBe(today);
    expect(current!.value.toString()).toBe("40880.36");
  });

  it("future values are the ones beyond today", () => {
    const future = futureOf(series, today);

    expect(future).toHaveLength(2);
    expect(future[0]!.date).toBe(of(2026, 9, 6));
  });

  it("conversions round trip", () => {
    const rate = money("40880.36");
    const uf = clpToUf(money("1000000"), rate);

    expectWithin(num(uf), 0.0001, 1000000 / 40880.36);
    expectWithin(num(ufToClp(uf, rate)), 5, 1000000);
  });

  it("conversion with a zero rate does not divide by zero", () => {
    expect(clpToUf(money("1000"), ZERO).toString()).toBe("0");
    expect(deltaPct(ZERO, money("10")).toString()).toBe("0");
  });

  it("deltas carry the right sign", () => {
    expect(delta(money("100"), money("102")).toString()).toBe("2");
    expectWithin(num(deltaPct(money("100"), money("102"))), 1e-9, 2);
    expectWithin(num(deltaPct(money("100"), money("98"))), 1e-9, -2);
  });

  it("an empty series has no current value", () => {
    expect(currentOf([], today)).toBeNull();
    expect(futureOf([], today)).toHaveLength(0);
  });

  // ------------------------------------------------------------ annualisation

  it("a full year annualises to its own change", () => {
    expectWithin(num(annualisedPct(money("100"), money("104"), 365)), 0.05, 4);
  });

  it("a half year annualises to roughly the compounded rate", () => {
    expectWithin(num(annualisedPct(money("100"), money("102"), 183)), 0.1, 4.04);
  });

  it("a decline annualises negative", () => {
    expectWithin(num(annualisedPct(money("100"), money("98"), 365)), 0.05, -2);
  });

  it("annualising a short window magnifies the move, as it should", () => {
    expect(num(annualisedPct(money("100"), money("101"), 30))).toBeGreaterThan(12);
  });

  it("annualisation refuses impossible inputs instead of returning nonsense", () => {
    expect(annualisedPct(money("100"), money("110"), 0).toString()).toBe("0");
    expect(annualisedPct(ZERO, money("110"), 365).toString()).toBe("0");
    expect(annualisedPct(money("100"), ZERO, 365).toString()).toBe("0");
  });

  it("real UF movement annualises close to Chilean inflation", () => {
    const annual = num(annualisedPct(money("39434.60"), money("40880.36"), 365));

    expect(annual).toBeGreaterThan(2);
    expect(annual).toBeLessThan(6);
  });

  // ------------------------------------------------------------- downsampling

  const long = Array.from({ length: 18_000 }, (_, k) => ({
    date: of(1977, 8, 1),
    value: money(String(1000 + k)),
  }));

  it("a short series is returned untouched", () => {
    const short = series.slice(0, 3);
    expect(downsample(short, 400)).toBe(short);
  });

  it("downsampling keeps the endpoints and the requested size", () => {
    const thinned = downsample(long, 400);

    expect(thinned).toHaveLength(400);
    expect(thinned[0]!.value.toString()).toBe(long[0]!.value.toString());
    expect(thinned[399]!.value.toString()).toBe(long[17_999]!.value.toString());
  });

  it("an absurd point budget does not crash", () => {
    expect(downsample(series, 1)).toBe(series);
    expect(downsample([], 400)).toHaveLength(0);
  });

  // ------------------------------------------------------------ history window

  const window = Array.from({ length: 400 }, (_, k) => ({
    date: of(2025, 9, 5),
    value: money(String(40000 + k)),
  })).map((v, k) => ({ ...v, date: shift(of(2025, 9, 5), k) }));

  it("the window never reaches past today", () => {
    const w = historyWindow(window, 3, of(2026, 9, 5));

    expect(w[w.length - 1]!.date).toBe(of(2026, 9, 5));
    expect(w.every((v) => v.date <= of(2026, 9, 5))).toBe(true);
  });

  it("the whole history also stops at today", () => {
    const w = historyWindow(window, null, of(2026, 9, 5));

    expect(w[0]!.date).toBe(of(2025, 9, 5));
    expect(w[w.length - 1]!.date).toBe(of(2026, 9, 5));
  });

  it("the window starts the requested number of months back", () => {
    expect(historyWindow(window, 3, of(2026, 9, 5))[0]!.date).toBe(of(2026, 6, 5));
  });

  it("a series entirely in the future yields nothing to chart", () => {
    const future = [
      { date: of(2026, 9, 6), value: money("40881.68") },
      { date: of(2026, 9, 9), value: money("40885.63") },
    ];
    expect(historyWindow(future, null, of(2026, 9, 5))).toHaveLength(0);
  });

  it("an empty series stays empty", () => {
    expect(historyWindow([], 3)).toHaveLength(0);
  });
});

function shift(start: string, days: number): string {
  const d = new Date(`${start}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
