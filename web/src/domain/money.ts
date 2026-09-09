import Big from "big.js";

/**
 * Money, ported from the Android app's BigDecimal discipline.
 *
 * JavaScript numbers are float64 and cannot hold two exact decimals, which is
 * the whole reason the Kotlin never used Double for money. big.js is the direct
 * analogue of BigDecimal, so the port stays mechanical instead of becoming an
 * interpretation.
 */
export type Money = Big;

// Plenty of room for intermediate division; every result is rounded explicitly
// at the point the Kotlin rounds it.
Big.DP = 30;
Big.RM = Big.roundHalfUp;

export const HALF_UP = 1 as const;

export const money = (v: Big.BigSource): Money => new Big(v);
export const ZERO: Money = new Big(0);
export const ONE: Money = new Big(1);
export const HUNDRED: Money = new Big(100);

/** BigDecimal.setScale(scale, HALF_UP) */
export const scale = (v: Money, dp: number): Money => v.round(dp, HALF_UP);

/** BigDecimal.divide(other, scale, HALF_UP) */
export const divScale = (a: Money, b: Money, dp: number): Money =>
  a.div(b).round(dp, HALF_UP);

export const isZero = (v: Money): boolean => v.cmp(0) === 0;
export const isPositive = (v: Money): boolean => v.cmp(0) > 0;
export const isNegative = (v: Money): boolean => v.cmp(0) < 0;
export const signum = (v: Money): number => v.cmp(0);

export const minOf = (a: Money, b: Money): Money => (a.cmp(b) <= 0 ? a : b);
export const maxOf = (a: Money, b: Money): Money => (a.cmp(b) >= 0 ? a : b);

/** Sum with a fixed scale, mirroring the Kotlin fold + setScale. */
export const sumScaled = (values: Money[], dp: number): Money =>
  scale(values.reduce((acc, v) => acc.plus(v), ZERO), dp);
