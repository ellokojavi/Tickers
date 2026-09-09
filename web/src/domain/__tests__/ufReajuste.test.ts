import { describe, expect, it } from "vitest";
import { money } from "../money.ts";
import { of } from "../dates.ts";
import { convert } from "../ufReajuste.ts";
import { expectWithin, num } from "../testing.ts";

const uf = (date: string, value: string) => ({ date, value: money(value) });

describe("uf reajuste", () => {
  it("restates by the ratio of the two UF values", () => {
    const r = convert(money("1000"), uf(of(2020, 1, 1), "28000.00"), uf(of(2021, 1, 1), "29000.00"));

    expectWithin(num(r.factor), 1e-6, 29000 / 28000);
    expectWithin(num(r.adjustedAmount), 0.5, (1000 * 29000) / 28000);
    expectWithin(num(r.ufUnits), 0.0001, 1000 / 28000);
  });

  it("four thousand pesos of january 1990 restate to about thirty thousand", () => {
    const r = convert(money("4000"), uf(of(1990, 1, 1), "5435.28"), uf(of(2026, 9, 5), "40880.36"));

    expectWithin(num(r.adjustedAmount), 1, 30085);
    expectWithin(num(r.factor), 0.0001, 7.5213);
    expect(r.days).toBe(13396);
    expectWithin(num(r.annualisedPct), 0.05, 5.66);
  });

  it("the same day is a no-op", () => {
    const day = uf(of(2020, 5, 5), "28000.00");
    const r = convert(money("1234"), day, day);

    expectWithin(num(r.adjustedAmount), 0.5, 1234);
    expectWithin(num(r.factor), 1e-9, 1);
    expectWithin(num(r.variationPct), 1e-9, 0);
    expectWithin(num(r.annualisedPct), 1e-9, 0);
    expect(r.days).toBe(0);
  });

  it("converting forward and back returns the original amount", () => {
    const a = uf(of(1995, 6, 15), "12000.00");
    const b = uf(of(2020, 6, 15), "28000.00");

    const forward = convert(money("100000"), a, b);
    const back = convert(forward.adjustedAmount, b, a);

    expectWithin(num(back.adjustedAmount), 1, 100000);
  });

  it("going backwards in time shrinks the amount", () => {
    const r = convert(money("30000"), uf(of(2026, 9, 5), "40880.36"), uf(of(1990, 1, 1), "5435.28"));

    expect(num(r.adjustedAmount)).toBeLessThan(30000);
    expect(r.variationPct.cmp(0)).toBeLessThan(0);
    expect(r.days).toBe(-13396);
  });

  it("the annual rate compounds back to the factor", () => {
    const r = convert(money("1000"), uf(of(2000, 1, 1), "15000.00"), uf(of(2020, 1, 1), "28000.00"));
    const rebuilt = Math.pow(1 + num(r.annualisedPct) / 100, r.days / 365.25);

    expectWithin(rebuilt, 0.01, num(r.factor));
  });

  it("two dates one day apart give different results", () => {
    const base = uf(of(2026, 9, 1), "40875.09");
    const a = convert(money("1000000"), base, uf(of(2026, 9, 4), "40879.04"));
    const b = convert(money("1000000"), base, uf(of(2026, 9, 5), "40880.36"));

    expect(b.adjustedAmount.cmp(a.adjustedAmount)).toBeGreaterThan(0);
  });

  it("large amounts do not drift from float rounding", () => {
    const r = convert(money("1000000000"), uf(of(2020, 1, 1), "28000.00"), uf(of(2020, 1, 2), "28001.00"));

    expectWithin(num(r.adjustedAmount), 1, (1_000_000_000 * 28001) / 28000);
  });

  it("a non-positive UF value is refused rather than dividing by zero", () => {
    expect(() => convert(money("1000"), uf(of(2020, 1, 1), "0"), uf(of(2021, 1, 1), "1"))).toThrow();
    expect(() => convert(money("1000"), uf(of(2020, 1, 1), "1"), uf(of(2021, 1, 1), "-1"))).toThrow();
  });
});
