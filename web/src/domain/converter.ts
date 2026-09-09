import { scale, type Money } from "./money.ts";
import { USD_SCALE } from "./fx.ts";
import { BTC_SCALE } from "./btc.ts";

/**
 * The three-field converters.
 *
 * Each screen has one amount and two derived from it, and the user may type in
 * any of the three. The trap is deriving one display value from another display
 * value: those have already been rounded for the eye, and rounding twice makes
 * the same quantity come out differently depending on which box was touched.
 * One bitcoin was showing 72.476.915 pesos on load and 72.476.920 after an
 * edit, purely from that.
 *
 * So every field is derived once, at full precision, from a single anchor. Only
 * the last step rounds, and the field the user is typing in is never rewritten
 * under their fingers.
 */

const UF_SCALE = 4;

export interface UfConversion {
  readonly uf: Money;
  readonly clp: Money;
  readonly usd: Money | null;
}

/**
 * UF is the anchor. `usdClp` is the observed dollar, and may be absent when the
 * app has not managed to fetch it, in which case dollars simply are not offered.
 */
export const ufConvert = (
  field: "uf" | "clp" | "usd",
  amount: Money,
  ufRate: Money,
  usdClp: Money | null,
): UfConversion | null => {
  if (ufRate.cmp(0) <= 0) return null;
  const uf = field === "uf" ? amount
    : field === "clp" ? amount.div(ufRate)
    : usdClp === null || usdClp.cmp(0) <= 0 ? null : amount.times(usdClp).div(ufRate);
  if (uf === null) return null;

  const clpExact = uf.times(ufRate);
  return {
    uf: scale(uf, UF_SCALE),
    clp: scale(clpExact, 0),
    usd: usdClp === null || usdClp.cmp(0) <= 0 ? null : scale(clpExact.div(usdClp), USD_SCALE),
  };
};

export interface BtcConversion {
  readonly btc: Money;
  readonly usd: Money;
  readonly clp: Money;
}

/** Bitcoin is the anchor, and the chain runs bitcoin to dollars to pesos. */
export const btcConvert = (
  field: "btc" | "usd" | "clp",
  amount: Money,
  btcUsd: Money,
  usdClp: Money,
): BtcConversion | null => {
  if (btcUsd.cmp(0) <= 0 || usdClp.cmp(0) <= 0) return null;
  const btc = field === "btc" ? amount
    : field === "usd" ? amount.div(btcUsd)
    : amount.div(usdClp).div(btcUsd);

  const usdExact = btc.times(btcUsd);
  return {
    btc: scale(btc, BTC_SCALE),
    usd: scale(usdExact, USD_SCALE),
    clp: scale(usdExact.times(usdClp), 0),
  };
};
