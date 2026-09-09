import type { Money } from "../domain/money.ts";
import { dayOfMonth, daysBetween, month, today as todayIso, year, type IsoDate } from "../domain/dates.ts";

/**
 * Chilean number and date formatting, ported from the Android app.
 *
 * Separators are chosen explicitly rather than inherited from the browser's
 * locale: the app is Chile-only and must read the same on a phone set to
 * English. Month names come from a table for the same reason, so no ICU
 * difference between browsers can change them.
 */
const groups = (digits: string): string => digits.replace(/\B(?=(\d{3})+(?!\d))/g, ".");

const fixed = (v: Money, dp: number): string => {
  const s = v.toFixed(dp, 1); // half-up
  const negative = s.startsWith("-");
  const body = negative ? s.slice(1) : s;
  const [int = "0", dec] = body.split(".");
  const out = dec === undefined ? groups(int) : `${groups(int)},${dec}`;
  return negative ? `-${out}` : out;
};

const num = (v: Money): number => Number(v.toString());

/** "$40.880" */
export const clp = (v: Money): string => `$${fixed(v, 0)}`;
/** "$40.880,36", where the cents matter. */
export const clpExact = (v: Money): string => `$${fixed(v, 2)}`;
/** "1.234,56 UF" */
export const uf = (v: Money): string => `${fixed(v, 2)} UF`;
/** "1.234,5678 UF", for payment tables. */
export const uf4 = (v: Money): string => `${fixed(v, 4)} UF`;
/** "7,5215x" */
export const factor = (v: Money): string => `${fixed(v, 4)}x`;
export const pct = (v: Money): string => `${fixed(v, 2)}%`;
export const pct1 = (v: Money): string => `${fixed(v, 1)}%`;

/** "+3,25%" */
export const pctSigned = (v: Money): string => {
  const s = fixed(v, 2).replace("-", "");
  const sign = v.cmp(0) > 0 ? "+" : v.cmp(0) < 0 ? "-" : "";
  return v.cmp(0) === 0 ? "0,00%" : `${sign}${s}%`;
};

/** "+$1,32" */
export const clpSigned = (v: Money): string => {
  const s = fixed(v, 2).replace("-", "");
  if (v.cmp(0) === 0) return "$0,00";
  return v.cmp(0) > 0 ? `+$${s}` : `-$${s}`;
};

/** "13.397". Any figure shown to a user is punctuated. */
export const integer = (v: number): string => {
  const negative = v < 0;
  const out = groups(String(Math.abs(Math.round(v))));
  return negative ? `-${out}` : out;
};

/** "36,7" */
export const decimal1 = (v: number): string => {
  const negative = v < 0;
  const s = Math.abs(v).toFixed(1);
  const [int = "0", dec = "0"] = s.split(".");
  return `${negative ? "-" : ""}${groups(int)},${dec}`;
};

/** "13.397 días (36,7 años)" */
export const period = (days: number): string => {
  const count = integer(days);
  // Only a span of exactly one day is singular. Zero is plural in Spanish
  // ("0 días"), and a reversed range is reachable — the inflation screen lets
  // either date be the later one — so -1 is singular too. The parenthetical
  // needs no such case: the months branch cannot start below "2,0 meses"
  // (60 / 30,44) nor the years branch below "2,0 años" (730 / 365,25), and a
  // decimal quantity takes the plural in any event.
  if (days === 1 || days === -1) return `${count} día`;
  if (days < 60) return `${count} días`;
  if (days < 730) return `${count} días (${decimal1(days / 30.44)} meses)`;
  return `${count} días (${decimal1(days / 365.25)} años)`;
};

const MONTHS = [
  "enero", "febrero", "marzo", "abril", "mayo", "junio",
  "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
] as const;
const MONTHS_SHORT = [
  "Ene", "Feb", "Mar", "Abr", "May", "Jun",
  "Jul", "Ago", "Sep", "Oct", "Nov", "Dic",
] as const;

/** "8 de septiembre de 2026" */
export const longDate = (d: IsoDate): string =>
  `${dayOfMonth(d)} de ${MONTHS[month(d) - 1]} de ${year(d)}`;

/** "08-09-2026" */
export const shortDate = (d: IsoDate): string =>
  `${String(dayOfMonth(d)).padStart(2, "0")}-${String(month(d)).padStart(2, "0")}-${year(d)}`;

export const monthYearShort = (d: IsoDate): string => `${MONTHS_SHORT[month(d) - 1]} ${year(d)}`;

// ------------------------------------------------ instants, in Chilean time
//
// A bitcoin chart is labelled with moments, not calendar days, and a moment
// has to be pinned to a zone or the two channels label the same candle
// differently depending on where the device thinks it is. This app is for
// Chile, so Chile is the zone, stated rather than inherited.
//
// Only the numeric parts come from Intl; the month names are this file's own,
// so the output cannot shift when the platform's locale data does.
const ZONE = "America/Santiago";

const parts = (at: number): Record<string, string> => {
  const formatter = new Intl.DateTimeFormat("en-GB", {
    timeZone: ZONE, hour12: false,
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit",
  });
  const out: Record<string, string> = {};
  for (const p of formatter.formatToParts(new Date(at))) out[p.type] = p.value;
  // Midnight comes back as "24" in some ICU versions.
  if (out.hour === "24") out.hour = "00";
  return out;
};

/** "14:35" */
export const timeHm = (at: number): string => {
  const p = parts(at);
  return `${p.hour}:${p.minute}`;
};

/** "08-09" — day and month, no year, for a span short enough that the year is obvious. */
export const dayMonthNum = (at: number): string => {
  const p = parts(at);
  return `${p.day}-${p.month}`;
};

/** "Sep 2026" */
export const monthYearOf = (at: number): string => {
  const p = parts(at);
  return `${MONTHS_SHORT[Number(p.month) - 1]} ${p.year}`;
};

/** "US$78.250,18" */
export const usd = (v: Money): string => `US$${fixed(v, 2)}`;

/** Day and month with no year, for labelling something already known to be recent. */
export const dayMonth = (d: IsoDate): string => `${dayOfMonth(d)} de ${MONTHS[month(d) - 1]}`;

/** "hoy", "ayer", "hace 3 días" */
export const relativeDay = (d: IsoDate, day: IsoDate = todayIso()): string => {
  const diff = daysBetween(d, day);
  if (diff === 0) return "hoy";
  if (diff === 1) return "ayer";
  if (diff > 1) return `hace ${integer(diff)} días`;
  if (diff === -1) return "mañana";
  return `en ${integer(-diff)} días`;
};

/** Parses input that may carry "." separators and a "," decimal. */
export const parseNumber = (text: string): number | null => {
  const cleaned = text.trim()
    .replaceAll("$", "").replaceAll(" ", "")
    .replace(/UF/gi, "")
    .replaceAll(".", "").replaceAll(",", ".");
  if (cleaned.length === 0) return null;
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : null;
};

export { num as toNumber };
