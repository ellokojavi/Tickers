import { money, type Money } from "../domain/money.ts";
import { of, parseIso, plusDays, type IsoDate, type YearMonth } from "../domain/dates.ts";
import type { DatedValue } from "../domain/models.ts";

/**
 * The complete daily UF series, shipped as a static asset.
 *
 * ~18.000 days from August 1977 compress to about 50 kB, so serving them with
 * the page is cheaper than fetching years on demand: every chart and every date
 * works offline, and afterwards only the current year ever needs refreshing.
 *
 * The file is not JSON. Days are contiguous, so it stores one start date and
 * then one value per line in centavos; an empty line is a day the source has no
 * value for. Parsing is a split plus a parseInt, with no tokeniser.
 *
 * It is the same file the Android app bundles, copied at build time.
 */
const START_KEY = "# inicio:";

export const parseSeed = (lines: readonly string[]): DatedValue[] => {
  const header = lines.find((l) => l.startsWith(START_KEY));
  const start = header === undefined ? null : parseIso(header.slice(START_KEY.length).trim());
  if (start === null) return [];

  const out: DatedValue[] = [];
  let day: IsoDate = start;
  for (const line of lines) {
    if (line.startsWith("#")) continue;
    const cents = line.trim();
    if (cents.length > 0) {
      const value = Number.parseInt(cents, 10);
      if (Number.isInteger(value) && String(value) === cents) {
        // Centavos to pesos with exactly two decimals, no float step.
        out.push({ date: day, value: money(cents).div(100) });
      }
    }
    day = plusDays(day, 1);
  }
  return out;
};

export const parseSeedText = (text: string): DatedValue[] => parseSeed(text.split("\n"));

/** UF value on the 9th of each month: the CPI index anchors. */
export const anchorsOf = (series: readonly DatedValue[]): Map<YearMonth, Money> => {
  const map = new Map<YearMonth, Money>();
  for (const v of series) {
    if (Number(v.date.slice(8, 10)) === 9) map.set(v.date.slice(0, 7), v.value);
  }
  return map;
};

export const SERIES_FIRST_DAY: IsoDate = of(1977, 8, 1);
