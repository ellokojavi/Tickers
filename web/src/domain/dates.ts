/**
 * Calendar dates as ISO strings, ported from java.time.LocalDate.
 *
 * A plain "YYYY-MM-DD" string rather than a Date object: it has no time and no
 * zone, so it cannot drift across midnight or shift when a user travels, which
 * is the classic way date arithmetic goes wrong in JavaScript. Comparison is
 * lexicographic, which for this format is chronological. All arithmetic goes
 * through UTC internally so no local offset can leak in.
 */
export type IsoDate = string;

const ISO = /^\d{4}-\d{2}-\d{2}$/;

const toUtc = (d: IsoDate): Date => new Date(`${d}T00:00:00Z`);

const fromUtc = (d: Date): IsoDate => d.toISOString().slice(0, 10);

export const isValidIso = (value: string): boolean => {
  if (!ISO.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && fromUtc(parsed) === value;
};

export const parseIso = (value: string): IsoDate | null =>
  isValidIso(value) ? value : null;

export const today = (): IsoDate => {
  // The user's own calendar day, not UTC's.
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
};

export const of = (year: number, month: number, day: number): IsoDate =>
  `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;

export const year = (d: IsoDate): number => Number(d.slice(0, 4));
export const month = (d: IsoDate): number => Number(d.slice(5, 7));
export const dayOfMonth = (d: IsoDate): number => Number(d.slice(8, 10));

export const compare = (a: IsoDate, b: IsoDate): number =>
  a < b ? -1 : a > b ? 1 : 0;
export const isBefore = (a: IsoDate, b: IsoDate): boolean => a < b;
export const isAfter = (a: IsoDate, b: IsoDate): boolean => a > b;

export const plusDays = (d: IsoDate, days: number): IsoDate => {
  const t = toUtc(d);
  t.setUTCDate(t.getUTCDate() + days);
  return fromUtc(t);
};

const lastDayOfMonth = (y: number, m: number): number =>
  new Date(Date.UTC(y, m, 0)).getUTCDate();

/**
 * Adds months, clamping to the last valid day exactly as java.time does:
 * 31 January plus one month is 28 February, and the day recovers afterwards.
 * The mortgage schedule depends on this.
 */
export const plusMonths = (d: IsoDate, months: number): IsoDate => {
  const y = year(d);
  const m = month(d);
  const day = dayOfMonth(d);

  const total = y * 12 + (m - 1) + months;
  const newYear = Math.floor(total / 12);
  const newMonth = (total % 12) + 1;
  const clamped = Math.min(day, lastDayOfMonth(newYear, newMonth));
  return of(newYear, newMonth, clamped);
};

export const daysBetween = (from: IsoDate, to: IsoDate): number =>
  Math.round((toUtc(to).getTime() - toUtc(from).getTime()) / 86_400_000);

/** Year and month only, as "YYYY-MM", for the monthly anchors. */
export type YearMonth = string;
export const yearMonthOf = (d: IsoDate): YearMonth => d.slice(0, 7);
export const plusMonthsYm = (ym: YearMonth, months: number): YearMonth =>
  yearMonthOf(plusMonths(`${ym}-01`, months));
