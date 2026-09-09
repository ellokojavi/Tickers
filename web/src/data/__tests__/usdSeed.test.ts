import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, resolve as resolvePath } from "node:path";
import { fileURLToPath } from "node:url";
import { parseSeedText } from "../seed.ts";
import { daysBetween, of } from "../../domain/dates.ts";

/**
 * Guards the bundled dólar observado series, which is regenerated every morning
 * by a workflow and committed without a human looking at it. These are the
 * checks that stop a bad regeneration from shipping.
 *
 * The file is read from the Android assets rather than the web copy, because
 * that is the source of truth and the web copy is a build artefact.
 */
const here = dirname(fileURLToPath(import.meta.url));
const series = parseSeedText(
  readFileSync(resolvePath(here, "../../../../app/src/main/assets/usd_daily.txt"), "utf8"),
);

describe("the bundled dollar series", () => {
  it("starts where the source's history does and reaches the present", () => {
    expect(series.length).toBeGreaterThan(10_000);
    expect(series[0]!.date).toBe(of(1984, 1, 2));
    expect(series.at(-1)!.date >= of(2026, 9, 1)).toBe(true);
  });

  it("is in date order with no repeats", () => {
    for (let i = 1; i < series.length; i++) {
      expect(series[i]!.date > series[i - 1]!.date).toBe(true);
    }
  });

  it("holds only positive values in a plausible range", () => {
    for (const v of series) {
      expect(v.value.cmp(0)).toBeGreaterThan(0);
      // The peso has never traded outside this band against the dollar in the
      // period covered, so anything beyond it is a bad parse or a bad source.
      expect(v.value.cmp(10)).toBeGreaterThan(0);
      expect(v.value.cmp(5000)).toBeLessThan(0);
    }
  });

  it("keeps the September 1984 devaluation, which is real history", () => {
    // A naive plausibility filter deletes this, because a 23,5% move in one day
    // looks exactly like corruption until you notice the new level held.
    const at = (d: string) => series.find((v) => v.date === d)?.value.toFixed(2);
    expect(at(of(1984, 9, 20))).toBe("93.13");
    expect(at(of(1984, 9, 21))).toBe("114.98");
    expect(at(of(1984, 9, 24))).toBe("115.05");
  });

  it("has no move so large it can only be a bad value", () => {
    for (let i = 1; i < series.length; i++) {
      const previous = series[i - 1]!;
      const current = series[i]!;
      const elapsed = Math.max(1, daysBetween(previous.date, current.date));
      const perDay = Math.abs(
        Number(current.value.div(previous.value).minus(1).toString()),
      ) / elapsed;
      // The 1984 devaluation is the largest real single-day move in the series.
      expect(perDay).toBeLessThan(0.25);
    }
  });

  it("has gaps only where the market is closed", () => {
    // Weekends and holidays have no observed dollar. What must never happen is
    // a long silence, which would mean the source stopped answering for a year
    // and nobody noticed.
    let longest = 0;
    for (let i = 1; i < series.length; i++) {
      longest = Math.max(longest, daysBetween(series[i - 1]!.date, series[i]!.date));
    }
    expect(longest).toBeLessThan(15);
  });
});
