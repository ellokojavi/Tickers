import Big from "big.js";

/**
 * Money, as an exact decimal.
 *
 * JavaScript numbers are float64 and cannot hold two exact decimals, so a
 * plain number never holds a peso anywhere in this app. big.js gives arbitrary
 * precision and explicit rounding, which is the whole requirement.
 */
export type Money = Big;

// Plenty of room for intermediate division; every result is rounded explicitly,
// once, at the point a figure is shown.
Big.DP = 30;
Big.RM = Big.roundHalfUp;

export const HALF_UP = 1 as const;

export const money = (v: Big.BigSource): Money => new Big(v);
export const ZERO: Money = new Big(0);
export const ONE: Money = new Big(1);
export const HUNDRED: Money = new Big(100);

/** Round to `dp` decimal places, half away from zero. */
export const scale = (v: Money, dp: number): Money => v.round(dp, HALF_UP);

/** Divide, rounding the result to `dp` places the same way. */
export const divScale = (a: Money, b: Money, dp: number): Money =>
  a.div(b).round(dp, HALF_UP);

export const isZero = (v: Money): boolean => v.cmp(0) === 0;
export const isPositive = (v: Money): boolean => v.cmp(0) > 0;
export const isNegative = (v: Money): boolean => v.cmp(0) < 0;
export const signum = (v: Money): number => v.cmp(0);

export const minOf = (a: Money, b: Money): Money => (a.cmp(b) <= 0 ? a : b);
export const maxOf = (a: Money, b: Money): Money => (a.cmp(b) >= 0 ? a : b);

/** Sum, rounded once at the end rather than term by term. */
export const sumScaled = (values: Money[], dp: number): Money =>
  scale(values.reduce((acc, v) => acc.plus(v), ZERO), dp);
