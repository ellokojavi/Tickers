import { readFileSync } from "node:fs";
import { dirname, resolve as resolvePath } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { anchorsOf, parseSeed, parseSeedText } from "../seed.ts";
import { filterImplausible } from "../../domain/ufSanity.ts";
import { daysBetween, of } from "../../domain/dates.ts";
import { expectWithin } from "../../domain/testing.ts";

const here = dirname(fileURLToPath(import.meta.url));
const lines = (...data: string[]) =>
  ["# comentario", "# inicio: 2026-09-01", "# unidad: centavos", ...data];

/**
 * The source of truth, not the copy the web build makes of it. Validating the
 * file that feeds both platforms is the point, and it means these run without
 * a build step having happened first.
 */
const shipped = () =>
  parseSeedText(readFileSync(
    resolvePath(here, "../../../../app/src/main/assets/uf_daily.txt"), "utf8",
  ));

describe("bundled series", () => {
  it("values are read as centavos into exact two-decimal pesos", () => {
    const parsed = parseSeed(lines("4088036", "4088168"));

    expect(parsed).toHaveLength(2);
    expect(parsed[0]!.date).toBe(of(2026, 9, 1));
    expect(parsed[0]!.value.toString()).toBe("40880.36");
    expect(parsed[1]!.date).toBe(of(2026, 9, 2));
  });

  /** A blank line is a day the source lacks. It must still shift the calendar. */
  it("a gap is skipped without shifting later dates", () => {
    const parsed = parseSeed(lines("4088036", "", "4088300"));

    expect(parsed).toHaveLength(2);
    expect(parsed[1]!.date).toBe(of(2026, 9, 3));
  });

  it("a missing or unreadable header yields nothing rather than wrong dates", () => {
    expect(parseSeed(["# sin inicio", "123"])).toHaveLength(0);
    expect(parseSeed(["# inicio: no-es-fecha", "123"])).toHaveLength(0);
    expect(parseSeed([])).toHaveLength(0);
  });

  it("junk lines are ignored, not guessed at", () => {
    const parsed = parseSeed(lines("4088036", "abc", "4088300"));

    expect(parsed).toHaveLength(2);
    expect(parsed[1]!.date).toBe(of(2026, 9, 3));
  });

  // --------------------------------------------- the file that actually ships

  it("covers the whole series with no missing day", () => {
    const parsed = shipped();

    expect(parsed.length).toBeGreaterThanOrEqual(17_900);
    expect(parsed[0]!.date).toBe(of(1977, 8, 1));
    expect(parsed.length).toBe(daysBetween(parsed[0]!.date, parsed[parsed.length - 1]!.date) + 1);
  });

  it("contains no implausible day-over-day jump", () => {
    const parsed = shipped();

    for (let k = 0; k < parsed.length - 1; k++) {
      const a = Number(parsed[k]!.value.toString());
      const b = Number(parsed[k + 1]!.value.toString());
      expect(Math.abs(b / a - 1)).toBeLessThan(0.01);
    }
  });

  it("survives its own runtime sanity filter untouched", () => {
    const parsed = shipped();
    expect(filterImplausible(parsed)).toHaveLength(parsed.length);
  });

  /**
   * The source serves 608,15 and 607,38 for 29 and 30 December 2014. The UF was
   * frozen at 24.627,10 across that whole re-adjustment period, so the true
   * values are recovered exactly rather than left as holes.
   */
  it("the corrupt December 2014 days carry their real value", () => {
    const byDate = new Map(shipped().map((v) => [v.date, v]));

    for (const day of [28, 29, 30, 31]) {
      const v = byDate.get(of(2014, 12, day));
      expect(v).toBeDefined();
      expectWithin(Number(v!.value.toString()), 0.005, 24627.1);
    }
  });

  /**
   * The UF is re-adjusted so its value on the 9th of month M+1 over the 9th of
   * month M equals the CPI variation of month M-1. That relationship is what
   * makes the series usable as a price index, so it is checked against the CPI
   * the INE actually published.
   */
  it("reproduces the published CPI for every month of 2024", () => {
    const a = anchorsOf(shipped());
    const published: Record<number, number> = {
      2: 0.6, 3: 0.4, 4: 0.5, 5: 0.3, 6: -0.1, 7: 0.7,
      8: 0.3, 9: 0.1, 10: 1.0, 11: 0.2, 12: -0.2,
    };
    const ym = (year: number, month: number) =>
      `${year}-${String(month).padStart(2, "0")}`;

    for (const [key, expected] of Object.entries(published)) {
      const m = Number(key);
      const before = a.get(m + 1 > 12 ? ym(2025, m + 1 - 12) : ym(2024, m + 1));
      const after = a.get(m + 2 > 12 ? ym(2025, m + 2 - 12) : ym(2024, m + 2));
      expect(before).toBeDefined();
      expect(after).toBeDefined();

      const derived = (Number(after!.toString()) / Number(before!.toString()) - 1) * 100;
      expectWithin(derived, 0.05, expected);
    }
  });
});
