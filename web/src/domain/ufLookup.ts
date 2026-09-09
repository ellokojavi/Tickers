import { daysBetween, isAfter, isBefore, of, type IsoDate } from "./dates.ts";
import type { UfValue } from "./models.ts";

/**
 * Outcome of asking what the UF was worth on a date.
 *
 * A closed set rather than a nullable value, because the wrong answers here are
 * the dangerous ones: silently substituting a neighbouring day, or presenting
 * an unpublished date as official. Both of those shipped once.
 */
export type LookupResult =
  | { kind: "exact"; value: UfValue; isFuture: boolean }
  | { kind: "nearest"; value: UfValue; requested: IsoDate }
  | { kind: "notPublishedYet"; lastPublished: IsoDate | null }
  | { kind: "beforeCoverage"; earliest: IsoDate }
  | { kind: "unavailable" }
  | { kind: "loading" };

/** First day of the daily UF series. */
export const SERIES_START: IsoDate = of(1977, 8, 1);

/**
 * Classifies a lookup. Pure, so the rules are tested without a browser.
 *
 * Order matters: coverage bounds are checked before any value is offered, so a
 * date past the horizon can never be answered with a neighbouring day's value.
 */
export const resolve = (
  requested: IsoDate,
  exact: UfValue | null,
  nearestEarlier: UfValue | null,
  lastPublished: IsoDate | null,
  day: IsoDate,
  earliest: IsoDate = SERIES_START,
): LookupResult => {
  if (isBefore(requested, earliest)) return { kind: "beforeCoverage", earliest };

  // The UF is published to the 9th of next month and no further.
  if (lastPublished !== null && isAfter(requested, lastPublished)) {
    return { kind: "notPublishedYet", lastPublished };
  }
  if (exact !== null) return { kind: "exact", value: exact, isFuture: isAfter(requested, day) };
  if (nearestEarlier !== null) return { kind: "nearest", value: nearestEarlier, requested };
  return { kind: "unavailable" };
};

/** Latest date a user may ask about: the last published day. */
export const maxSelectable = (series: readonly UfValue[]): IsoDate | null => {
  let max: IsoDate | null = null;
  for (const v of series) if (max === null || v.date > max) max = v.date;
  return max;
};

export const daysApart = daysBetween;
