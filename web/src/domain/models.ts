import { ZERO, type Money } from "./money.ts";
import type { IsoDate } from "./dates.ts";

/**
 * A calendar day and an amount, and nothing more.
 *
 * The UF and the dólar observado are both published this way, so both are
 * series of these. Bitcoin is not: its points are timestamps rather than days,
 * which is why the chart takes anything with a `value` rather than this.
 */
export interface DatedValue {
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

/**
 * The companion indicators, in the order they are shown, and how each one's
 * date reads.
 *
 * The order is the decision, so it lives here rather than being whatever the
 * source happened to serialise. It runs from the figures that measure the
 * reader's own money to the ones that describe the country around it: the unit
 * taxes and fines are owed in, then the two rates that price money and are
 * what the app's own calculators are built on, then the other currencies and
 * the commodity that moves ours, then the UF's forgotten sibling, and last the
 * two macro readings, which are worth knowing and are not money you hold.
 *
 * The dólar observado is deliberately absent **from this list**: it has a card
 * of its own at the top of the screen, and listing it twice invites the reader
 * to wonder which of the two is the real one. It is still fetched — see
 * `FETCHED_CODES` — because three screens convert through it.
 *
 * Two the source publishes are deliberately never shown. `dolar_intercambio`
 * has not moved since 2014, so it would be a dead figure presented as a
 * current one. `bitcoin` is stale here and already has a live card above.
 *
 * `cadence` is not decoration: a daily figure is dated to the day and a
 * monthly one to its month, and printing "1 de septiembre" for a value that
 * means "September" is a small lie that reads as a large one when the value
 * turns out to be nine months old.
 */
export const COMPANIONS = [
  { code: "utm", cadence: "monthly" },
  { code: "tpm", cadence: "daily" },
  { code: "ipc", cadence: "monthly" },
  { code: "euro", cadence: "daily" },
  { code: "libra_cobre", cadence: "daily" },
  { code: "ivp", cadence: "daily" },
  { code: "imacec", cadence: "monthly" },
  { code: "tasa_desempleo", cadence: "monthly" },
] as const;

export type CompanionCode = (typeof COMPANIONS)[number]["code"];
export type Cadence = (typeof COMPANIONS)[number]["cadence"];

export const cadenceOf = (code: string): Cadence =>
  COMPANIONS.find((c) => c.code === code)?.cadence ?? "daily";

export const isCompanion = (code: string): boolean =>
  COMPANIONS.some((c) => c.code === code);

/**
 * What the app asks the source for, which is not the same as what it lists.
 *
 * `dolar` is here and not in COMPANIONS: the UF converter's dollar field, the
 * bitcoin card on the overview and the bitcoin screen all convert through the
 * Banco Central's observed rate, and all three read it from here. Dropping it
 * from the request because it had been dropped from a list is exactly the
 * mistake this separation exists to prevent — it silently cost the bitcoin
 * card its peso figure once.
 */
export const FETCHED_CODES: readonly string[] = [
  ...COMPANIONS.map((c) => c.code),
  "dolar",
];

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

