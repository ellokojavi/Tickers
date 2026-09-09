import { HUNDRED, ONE, ZERO, divScale, money, scale, type Money } from "./money.ts";
import { daysBetween } from "./dates.ts";
import type { ReajusteResult, UfValue } from "./models.ts";

/**
 * Restates an amount between two dates through the UF.
 *
 * This is the mechanism Chilean contracts, rents and debts are actually
 * re-adjusted with, and because the UF is published every calendar day it
 * answers at day precision. The UF tracks the CPI with the lag it is built
 * with, so the variation it reports is inflation as the UF carries it, not the
 * INE's month-on-month figure.
 */
const DAYS_IN_YEAR = 365.25;

export const convert = (amount: Money, from: UfValue, to: UfValue): ReajusteResult => {
  if (from.value.cmp(0) <= 0 || to.value.cmp(0) <= 0) {
    throw new Error("El valor de la UF debe ser positivo");
  }

  const units = divScale(amount, from.value, 4);
  const factor = to.value.div(from.value);
  const days = daysBetween(from.date, to.date);

  let annualised = ZERO;
  if (days !== 0) {
    const years = days / DAYS_IN_YEAR;
    const a = Math.pow(Number(factor.toString()), 1 / years) - 1;
    if (Number.isFinite(a)) annualised = scale(money((a * 100).toString()), 2);
  }

  return {
    amount,
    from: from.date,
    to: to.date,
    ufAtFrom: from.value,
    ufAtTo: to.value,
    ufUnits: units,
    // Rounded once, at the end: converting through UF units and rounding twice
    // would drift on large amounts.
    adjustedAmount: scale(amount.times(factor), 0),
    factor: scale(factor, 6),
    variationPct: scale(factor.minus(ONE).times(HUNDRED), 2),
    annualisedPct: annualised,
    days,
  };
};
