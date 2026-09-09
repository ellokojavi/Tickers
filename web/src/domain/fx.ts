import { divScale, isZero, scale, ZERO, type Money } from "./money.ts";

/**
 * Pesos and dollars.
 *
 * The rate is always the Banco Central's dólar observado, which is published
 * once a business day. Nothing here knows that; it is the screens' job to say
 * which day's rate they used, because a conversion carrying no date invites
 * being read as live.
 *
 * Pesos have no cents in Chile, so a peso amount rounds to the unit. Dollars
 * keep two.
 */
export const USD_SCALE = 2;

export const usdToClp = (usd: Money, usdClp: Money): Money => scale(usd.times(usdClp), 0);

export const clpToUsd = (clp: Money, usdClp: Money): Money =>
  isZero(usdClp) ? ZERO : divScale(clp, usdClp, USD_SCALE);
