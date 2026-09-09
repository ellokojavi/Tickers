import {
  ZERO, HUNDRED, divScale, isZero, money, scale, type Money,
} from "./money.ts";
import { daysBetween, isAfter, isBefore, plusMonths, today as todayIso, type IsoDate } from "./dates.ts";
import type { UfValue } from "./models.ts";

/** Conversions, deltas and windowing over the UF series. */

export const clpToUf = (clp: Money, ufValue: Money): Money =>
  isZero(ufValue) ? ZERO : divScale(clp, ufValue, 4);

export const ufToClp = (uf: Money, ufValue: Money): Money =>
  scale(uf.times(ufValue), 0);

export const delta = (from: Money, to: Money): Money => to.minus(from);

export const deltaPct = (from: Money, to: Money): Money =>
  isZero(from) ? ZERO : scale(divScale(to.minus(from), from, 8).times(HUNDRED), 2);

/**
 * Constant annual rate reproducing the change over [days]. Short windows give
 * large figures; that is arithmetic, which is why the UI labels it.
 */
export const annualisedPct = (from: Money, to: Money, days: number): Money => {
  if (days <= 0 || from.cmp(0) <= 0 || to.cmp(0) <= 0) return ZERO;
  const years = days / 365.25;
  const factor = Number(to.toString()) / Number(from.toString());
  const annual = (Math.pow(factor, 1 / years) - 1) * 100;
  if (!Number.isFinite(annual)) return ZERO;
  return scale(money(annual.toString()), 2);
};

/**
 * A window shorter than this is not annualised. Three weeks: a 1M window of a
 * business-day series still qualifies whatever weekday it opens on, and a
 * 7-day one never does. Compounding a week into a year gives a figure that is
 * arithmetic but not information.
 */
export const ANNUALISE_MIN_DAYS = 21;

/**
 * What every history chart says above its line: the change over the window
 * and, when the window is long enough to mean anything, the same change as a
 * constant annual rate. Every chart in the app reads its window through this
 * so they cannot disagree about when to annualise.
 */
export interface ChartSummary {
  readonly periodPct: Money;
  readonly annualisedPct: Money | null;
}

export const chartSummary = (from: Money, to: Money, days: number): ChartSummary => ({
  periodPct: deltaPct(from, to),
  annualisedPct: days >= ANNUALISE_MIN_DAYS ? annualisedPct(from, to, days) : null,
});

/**
 * The last value that is not in the future. The series legitimately contains
 * days already published beyond today.
 */
export const currentOf = (series: readonly UfValue[], day: IsoDate): UfValue | null => {
  let best: UfValue | null = null;
  for (const v of series) {
    if (!isAfter(v.date, day) && (best === null || v.date > best.date)) best = v;
  }
  return best;
};

export const futureOf = (series: readonly UfValue[], day: IsoDate): UfValue[] =>
  series.filter((v) => isAfter(v.date, day)).sort((a, b) => (a.date < b.date ? -1 : 1));

/**
 * The slice a chart should draw: the last [months] up to and including today,
 * never beyond it. The series runs past today, but a historical chart is a
 * record of what has happened; those days have their own card on the screen.
 */
export const historyWindow = (
  series: readonly UfValue[],
  months: number | null,
  day: IsoDate = todayIso(),
): UfValue[] => {
  const history = series.filter((v) => !isAfter(v.date, day));
  if (months === null) return history;
  const from = plusMonths(day, -months);
  return history.filter((v) => !isBefore(v.date, from));
};

/**
 * Evenly thins to at most [maxPoints], always keeping the first and last.
 *
 * The full series is nearly 18.000 days and a chart cannot usefully draw more
 * points than it has pixels; without this, dragging across "Máx" would rebuild
 * an 18.000-segment path on every pointer event.
 */
export const downsample = (values: readonly UfValue[], maxPoints: number): readonly UfValue[] => {
  if (maxPoints < 2 || values.length <= maxPoints) return values;
  const step = (values.length - 1) / (maxPoints - 1);
  const out: UfValue[] = [];
  for (let i = 0; i < maxPoints; i++) {
    const index = Math.min(values.length - 1, Math.max(0, Math.round(i * step)));
    out.push(values[index]!);
  }
  return out;
};

export const daysBetweenDates = daysBetween;
