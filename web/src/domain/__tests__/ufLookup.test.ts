import { describe, expect, it } from "vitest";
import { money } from "../money.ts";
import { of } from "../dates.ts";
import { SERIES_START, maxSelectable, resolve } from "../ufLookup.ts";

const today = of(2026, 9, 5);
const lastPublished = of(2026, 9, 9);
const uf = (date: string, value = "40880.36") => ({ date, value: money(value) });

describe("uf lookup", () => {
  /**
   * The regression this exists for: asking about a date past the published
   * horizon used to answer with the last published day's value, labelled
   * official.
   */
  it("a date beyond the horizon is never answered with another day's value", () => {
    const r = resolve(of(2026, 11, 28), null, uf(lastPublished, "40885.63"), lastPublished, today);

    expect(r.kind).toBe("notPublishedYet");
    if (r.kind === "notPublishedYet") expect(r.lastPublished).toBe(lastPublished);
  });

  it("the day after the horizon is already unpublished", () => {
    expect(resolve(of(2026, 9, 10), null, uf(lastPublished), lastPublished, today).kind)
      .toBe("notPublishedYet");
  });

  it("the horizon itself is a published future value", () => {
    const r = resolve(lastPublished, uf(lastPublished, "40885.63"), null, lastPublished, today);

    expect(r.kind).toBe("exact");
    if (r.kind === "exact") expect(r.isFuture).toBe(true);
  });

  it("today is exact and not future", () => {
    const r = resolve(today, uf(today), null, lastPublished, today);

    expect(r.kind).toBe("exact");
    if (r.kind === "exact") expect(r.isFuture).toBe(false);
  });

  it("a gap inside coverage falls back to the nearest earlier day", () => {
    const nearest = uf(of(2010, 3, 14), "20999.00");
    const r = resolve(of(2010, 3, 15), null, nearest, lastPublished, today);

    expect(r.kind).toBe("nearest");
    if (r.kind === "nearest") expect(r.value.date).toBe(of(2010, 3, 14));
  });

  it("dates before the series start are rejected", () => {
    expect(resolve(of(1970, 1, 1), null, uf(SERIES_START), lastPublished, today).kind)
      .toBe("beforeCoverage");
  });

  it("the first day of the series is inside coverage", () => {
    expect(resolve(SERIES_START, uf(SERIES_START, "389.10"), null, lastPublished, today).kind)
      .toBe("exact");
  });

  it("nothing cached and nothing nearby is unavailable, not wrong", () => {
    expect(resolve(of(1990, 5, 5), null, null, lastPublished, today).kind).toBe("unavailable");
  });

  it("an empty cache does not fabricate a horizon", () => {
    expect(resolve(of(2026, 11, 28), null, null, null, today).kind).toBe("unavailable");
  });

  it("maxSelectable is the last published day, future included", () => {
    expect(maxSelectable([uf(today), uf(lastPublished), uf(of(2026, 9, 1))])).toBe(lastPublished);
    expect(maxSelectable([])).toBeNull();
  });
});
