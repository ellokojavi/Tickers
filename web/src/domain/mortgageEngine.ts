import {
  HALF_UP, HUNDRED, ONE, ZERO, divScale, minOf, money, scale, sumScaled, type Money,
} from "./money.ts";
import { plusMonths } from "./dates.ts";
import {
  loanAmountUf, termMonths,
  type AmortizationRow, type MortgageInput, type MortgageResult, type Prepayment,
  type RateConventionKey,
} from "./models.ts";

/**
 * Builds the amortisation schedule of a UF-denominated mortgage (UF + x%).
 *
 * French system: a constant capital-plus-interest payment. Insurance is charged
 * on top and is deliberately not constant, since the desgravamen premium
 * follows the outstanding balance.
 *
 * Every amount is a decimal at MONEY_SCALE places. Nothing here uses a float
 * for money; floats appear only where a rate is being solved for.
 */
export const MONEY_SCALE = 4;
const RATE_SCALE = 12;
/** (1+i)^n keeps about this many places, matching the Kotlin's DECIMAL64. */
const POWER_SCALE = 20;

export const monthlyRate = (annualRatePct: Money, convention: RateConventionKey): Money => {
  const annual = divScale(annualRatePct, HUNDRED, RATE_SCALE);
  if (convention === "NOMINAL_DIVIDED") return divScale(annual, money(12), RATE_SCALE);
  // The twelfth root has no exact decimal form; solving it in a float is fine
  // because this is a rate, not an amount.
  const m = Math.pow(1 + Number(annual.toString()), 1 / 12) - 1;
  return scale(money(m.toFixed(RATE_SCALE)), RATE_SCALE);
};

/** Constant French payment for [principal] at [i] per month over [n] months. */
export const payment = (principal: Money, i: Money, n: number): Money => {
  if (n <= 0) return scale(ZERO, MONEY_SCALE);
  if (i.cmp(0) === 0) return divScale(principal, money(n), MONEY_SCALE);
  const powed = ONE.plus(i).pow(n).round(POWER_SCALE, HALF_UP);
  const discount = ONE.div(powed); // (1+i)^-n
  return divScale(principal.times(i), ONE.minus(discount), MONEY_SCALE);
};

export const simulate = (input: MortgageInput): MortgageResult => {
  const i = monthlyRate(input.annualRatePct, input.rateConvention);
  const principal = scale(loanAmountUf(input), MONEY_SCALE);
  const n = termMonths(input);
  const basePayment = payment(principal, i, n);

  const lifeRate = divScale(input.lifeInsuranceMonthlyPct, HUNDRED, RATE_SCALE);
  const fire = scale(input.fireInsuranceMonthlyUf, MONEY_SCALE);

  const byMonth = new Map<number, Prepayment[]>();
  for (const p of input.prepayments) {
    const list = byMonth.get(p.monthNumber) ?? [];
    list.push(p);
    byMonth.set(p.monthNumber, list);
  }

  const rows: AmortizationRow[] = [];
  let balance = principal;
  let currentPayment = basePayment;
  let month = 1;

  // A prepayment can only ever shorten the schedule, so n is a hard cap.
  while (balance.cmp(0) > 0 && month <= n) {
    const opening = balance;
    const interest = scale(opening.times(i), MONEY_SCALE);
    let principalPart = scale(currentPayment.minus(interest), MONEY_SCALE);
    let thisPayment = currentPayment;

    // Final instalment: settle whatever is left rather than overshoot.
    if (principalPart.cmp(opening) >= 0) {
      principalPart = opening;
      thisPayment = scale(principalPart.plus(interest), MONEY_SCALE);
    }
    // A negative-amortising payment would never repay the loan.
    if (principalPart.cmp(0) < 0) principalPart = scale(ZERO, MONEY_SCALE);

    const life = scale(opening.times(lifeRate), MONEY_SCALE);
    let afterPrincipal = scale(opening.minus(principalPart), MONEY_SCALE);

    let prepaid = scale(ZERO, MONEY_SCALE);
    const prepays = byMonth.get(month);
    if (prepays !== undefined && afterPrincipal.cmp(0) > 0) {
      for (const p of prepays) {
        const applied = minOf(scale(p.amountUf, MONEY_SCALE), afterPrincipal);
        if (applied.cmp(0) <= 0) continue;
        prepaid = prepaid.plus(applied);
        afterPrincipal = afterPrincipal.minus(applied);
        if (p.mode === "REDUCE_PAYMENT") {
          const remaining = n - month;
          currentPayment = remaining > 0 ? payment(afterPrincipal, i, remaining) : afterPrincipal;
        }
        // REDUCE_TERM keeps the payment; the loop simply ends sooner.
      }
    }

    rows.push({
      number: month,
      date: plusMonths(input.firstPaymentDate, month - 1),
      openingBalanceUf: opening,
      interestUf: interest,
      principalUf: principalPart,
      paymentUf: thisPayment,
      lifeInsuranceUf: life,
      fireInsuranceUf: fire,
      prepaymentUf: prepaid,
      totalOutflowUf: scale(thisPayment.plus(life).plus(fire).plus(prepaid), MONEY_SCALE),
      closingBalanceUf: afterPrincipal,
    });

    balance = afterPrincipal;
    month++;
  }

  const stampTax = divScale(principal.times(input.stampTaxPct), HUNDRED, MONEY_SCALE);
  const upfront = scale(
    input.originationFeeUf.plus(stampTax).plus(input.otherUpfrontCostsUf),
    MONEY_SCALE,
  );

  const totalInterest = sumScaled(rows.map((r) => r.interestUf), MONEY_SCALE);
  const totalLife = sumScaled(rows.map((r) => r.lifeInsuranceUf), MONEY_SCALE);
  const totalFire = sumScaled(rows.map((r) => r.fireInsuranceUf), MONEY_SCALE);
  const totalPrepay = sumScaled(rows.map((r) => r.prepaymentUf), MONEY_SCALE);
  const totalOutflow = sumScaled(rows.map((r) => r.totalOutflowUf), MONEY_SCALE);

  return {
    input,
    loanAmountUf: principal,
    monthlyRate: i,
    basePaymentUf: basePayment,
    firstTotalPaymentUf: rows[0]?.totalOutflowUf ?? scale(ZERO, MONEY_SCALE),
    schedule: rows,
    totalInterestUf: totalInterest,
    totalLifeInsuranceUf: totalLife,
    totalFireInsuranceUf: totalFire,
    totalPrepaymentsUf: totalPrepay,
    upfrontCostsUf: upfront,
    totalCostUf: scale(totalOutflow.plus(upfront), MONEY_SCALE),
    caePct: solveCae(principal, upfront, rows.map((r) => r.totalOutflowUf)),
    effectiveTermMonths: rows.length,
    monthsSaved: Math.max(0, n - rows.length),
  };
};

/**
 * Carga Anual Equivalente: the annual rate that makes the present value of
 * everything the borrower pays equal the net amount actually received.
 *
 * Solved by bisection on the monthly rate. Returns null when the cash flows
 * have no sign change and therefore no root.
 */
export const solveCae = (
  principal: Money,
  upfrontCosts: Money,
  outflows: readonly Money[],
): Money | null => {
  if (outflows.length === 0 || principal.cmp(0) <= 0) return null;
  const net = Number(principal.minus(upfrontCosts).toString());
  if (net <= 0) return null;
  const flows = outflows.map((f) => Number(f.toString()));

  const npv = (monthly: number): number => {
    let acc = net;
    let discount = 1;
    const step = 1 + monthly;
    for (const f of flows) {
      discount *= step;
      acc -= f / discount;
    }
    return acc;
  };

  let lo = -0.9999;
  let hi = 5;
  if (npv(lo) * npv(hi) > 0) return null;
  for (let k = 0; k < 200; k++) {
    const mid = (lo + hi) / 2;
    if (npv(lo) * npv(mid) <= 0) hi = mid;
    else lo = mid;
  }
  const monthly = (lo + hi) / 2;
  const annual = (Math.pow(1 + monthly, 12) - 1) * 100;
  if (!Number.isFinite(annual)) return null;
  return scale(money(annual.toFixed(10)), 2);
};
