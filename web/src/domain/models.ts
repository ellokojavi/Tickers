import { ZERO, type Money } from "./money.ts";
import type { IsoDate } from "./dates.ts";

export interface UfValue {
  readonly date: IsoDate;
  readonly value: Money;
}

export interface Indicator {
  readonly code: string;
  readonly name: string;
  readonly unit: string;
  readonly date: IsoDate;
  readonly value: Money;
}

export const DataSource = {
  CMF: { label: "CMF (oficial)", official: true },
  MINDICADOR: { label: "mindicador.cl", official: false },
  BUNDLED: { label: "Datos incluidos en la app", official: false },
  CACHE: { label: "Caché local", official: false },
} as const;
export type DataSourceKey = keyof typeof DataSource;

// ------------------------------------------------------------------ reajuste

export interface ReajusteResult {
  readonly amount: Money;
  readonly from: IsoDate;
  readonly to: IsoDate;
  readonly ufAtFrom: Money;
  readonly ufAtTo: Money;
  readonly ufUnits: Money;
  readonly adjustedAmount: Money;
  readonly factor: Money;
  readonly variationPct: Money;
  readonly annualisedPct: Money;
  readonly days: number;
}

// ------------------------------------------------------------------ mortgage

/**
 * Chilean lenders quote an annual rate but do not all convert it to a monthly
 * one the same way. Both are offered so a simulation can be matched against a
 * particular bank's quote.
 */
export const RateConvention = {
  NOMINAL_DIVIDED: "Nominal (anual / 12)",
  EFFECTIVE_EQUIVALENT: "Efectiva equivalente",
} as const;
export type RateConventionKey = keyof typeof RateConvention;

export const PrepaymentMode = {
  REDUCE_TERM: "Acortar plazo",
  REDUCE_PAYMENT: "Bajar la cuota",
} as const;
export type PrepaymentModeKey = keyof typeof PrepaymentMode;

export interface Prepayment {
  readonly monthNumber: number;
  readonly amountUf: Money;
  readonly mode: PrepaymentModeKey;
}

export interface MortgageInput {
  readonly propertyValueUf: Money;
  readonly downPaymentUf: Money;
  readonly annualRatePct: Money;
  readonly termYears: number;
  readonly rateConvention: RateConventionKey;
  /** Desgravamen: monthly percentage of the outstanding balance. */
  readonly lifeInsuranceMonthlyPct: Money;
  /** Incendio y sismo: a fixed UF amount every month. */
  readonly fireInsuranceMonthlyUf: Money;
  readonly originationFeeUf: Money;
  readonly stampTaxPct: Money;
  readonly otherUpfrontCostsUf: Money;
  /** Due date of instalment 1; later ones fall on the same day each month. */
  readonly firstPaymentDate: IsoDate;
  readonly prepayments: readonly Prepayment[];
}

export interface AmortizationRow {
  readonly number: number;
  readonly date: IsoDate;
  readonly openingBalanceUf: Money;
  readonly interestUf: Money;
  readonly principalUf: Money;
  /** Capital plus interest, before insurance. */
  readonly paymentUf: Money;
  readonly lifeInsuranceUf: Money;
  readonly fireInsuranceUf: Money;
  readonly prepaymentUf: Money;
  readonly totalOutflowUf: Money;
  readonly closingBalanceUf: Money;
}

export interface MortgageResult {
  readonly input: MortgageInput;
  readonly loanAmountUf: Money;
  readonly monthlyRate: Money;
  readonly basePaymentUf: Money;
  readonly firstTotalPaymentUf: Money;
  readonly schedule: readonly AmortizationRow[];
  readonly totalInterestUf: Money;
  readonly totalLifeInsuranceUf: Money;
  readonly totalFireInsuranceUf: Money;
  readonly totalPrepaymentsUf: Money;
  readonly upfrontCostsUf: Money;
  readonly totalCostUf: Money;
  /** Carga Anual Equivalente, in percent. Null when it cannot be solved. */
  readonly caePct: Money | null;
  readonly effectiveTermMonths: number;
  readonly monthsSaved: number;
}

export interface Simulation {
  readonly id: string;
  readonly name: string;
  readonly notes: string;
  readonly input: MortgageInput;
  readonly createdAt: number;
  readonly updatedAt: number;
}

const maxZeroFwd = (v: Money): Money => (v.cmp(0) < 0 ? ZERO : v);

export const loanAmountUf = (i: MortgageInput): Money =>
  maxZeroFwd(i.propertyValueUf.minus(i.downPaymentUf));

export const termMonths = (i: MortgageInput): number => i.termYears * 12;

export const financedPct = (i: MortgageInput): Money => {
  if (i.propertyValueUf.cmp(0) === 0) return ZERO;
  return loanAmountUf(i).div(i.propertyValueUf).round(6, 1).times(100);
};

