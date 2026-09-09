import { describe, expect, it } from "vitest";
import { money } from "../money.ts";
import { of } from "../dates.ts";
import { filterImplausible } from "../ufSanity.ts";

const uf = (day: number, value: string) => ({ date: of(2014, 12, day), value: money(value) });

describe("uf sanity filter", () => {
  /** The exact corruption the public source actually serves. */
  it("the real 2014 corruption is rejected", () => {
    const kept = filterImplausible([
      uf(28, "24627.10"), uf(29, "608.15"), uf(30, "607.38"), uf(31, "24627.10"),
    ]);

    expect(kept.map((v) => Number(v.date.slice(8)))).toEqual([28, 31]);
  });

  it("a normal series passes untouched", () => {
    expect(filterImplausible([
      uf(1, "24500.00"), uf(2, "24505.00"), uf(3, "24510.00"), uf(4, "24515.00"),
    ])).toHaveLength(4);
  });

  /** 0,2633% is the largest real daily move in 49 years; it must survive. */
  it("the largest genuine daily move is kept", () => {
    expect(filterImplausible([uf(1, "1000.00"), uf(2, "1002.633")])).toHaveLength(2);
  });

  it("the allowance scales with the gap between days", () => {
    expect(filterImplausible([uf(1, "1000.00"), uf(4, "1015.00")])).toHaveLength(2);
    expect(filterImplausible([uf(1, "1000.00"), uf(2, "1015.00")])).toHaveLength(1);
  });

  it("an anchor checks the first value of a batch too", () => {
    const anchor = { date: of(2014, 12, 28), value: money("24627.10") };
    expect(filterImplausible([uf(29, "608.15")], anchor)).toHaveLength(0);
  });

  it("without an anchor the first value is trusted", () => {
    expect(filterImplausible([uf(29, "608.15")])).toHaveLength(1);
  });

  it("non-positive values never pass", () => {
    const kept = filterImplausible([uf(1, "0"), uf(2, "-5"), uf(3, "24500.00")]);

    expect(kept).toHaveLength(1);
    expect(kept[0]!.date).toBe(of(2014, 12, 3));
  });

  it("input order does not matter and output is sorted", () => {
    const kept = filterImplausible([uf(3, "24510.00"), uf(1, "24500.00"), uf(2, "24505.00")]);

    expect(kept.map((v) => Number(v.date.slice(8)))).toEqual([1, 2, 3]);
  });

  it("an empty batch stays empty", () => {
    expect(filterImplausible([])).toHaveLength(0);
  });
});
