import { daysBetween } from "./dates.ts";
import type { DatedValue } from "./models.ts";

/**
 * Rejects values that cannot be real.
 *
 * Not defensive paranoia: the public feed has served 608,15 for 2014-12-29 when
 * the actual UF was about 24.627, and that value would otherwise have been
 * stored and charted as fact. The same feed serves the current year, so the
 * guard runs on every refresh, not only on the bundled data.
 *
 * The largest genuine day-over-day change in the whole 49-year series is
 * 0,2633%, so the threshold sits roughly four times above anything real.
 */
export const MAX_DAILY_CHANGE = 0.01;

/**
 * @param values incoming values, in any order.
 * @param anchor the last value already known to be good, so the first value of
 *   a batch is checked too.
 * @returns the survivors, in date order.
 */
export const filterImplausible = (
  values: readonly DatedValue[],
  anchor: DatedValue | null = null,
): DatedValue[] => {
  if (values.length === 0) return [];
  const sorted = [...values].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  const out: DatedValue[] = [];
  let previous = anchor;

  for (const candidate of sorted) {
    if (candidate.value.cmp(0) <= 0) continue;
    if (previous !== null) {
      const gap = Math.max(1, daysBetween(previous.date, candidate.date));
      const change = Math.abs(
        Number(candidate.value.toString()) / Number(previous.value.toString()) - 1,
      );
      if (change > MAX_DAILY_CHANGE * gap) continue;
    }
    out.push(candidate);
    previous = candidate;
  }
  return out;
};
