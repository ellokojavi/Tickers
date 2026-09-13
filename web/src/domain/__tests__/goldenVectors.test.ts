import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { money, type Money } from "../money.ts";
import { simulate } from "../mortgageEngine.ts";
import type { MortgageInput, PrepaymentModeKey, RateConventionKey } from "../models.ts";

/**
 * The mortgage engine is the highest-risk code in the app: the only place
 * where a silent error produces a plausible-looking wrong answer. A label that
 * changes is visible; a dividend that changes by a hundredth is not, and it is
 * the one that costs someone money.
 *
 * `web/golden/mortgage.json` holds inputs and their exact expected outputs at
 * the engine's own scale, checked as strings rather than within a tolerance.
 * It is not generated from the engine — that would make it agree with whatever
 * the engine currently does. Changing a figure on purpose means editing the
 * file deliberately, which is exactly the moment worth pausing at.
 *
 * Every reported miscalculation should arrive here as a new case before it is
 * fixed.
 */

interface RawInput {
  propertyValueUf: string; downPaymentUf: string; annualRatePct: string;
  termYears: number; rateConvention: RateConventionKey;
  lifeInsuranceMonthlyPct: string; fireInsuranceMonthlyUf: string;
  originationFeeUf: string; stampTaxPct: string; otherUpfrontCostsUf: string;
  firstPaymentDate: string;
  prepayments: { monthNumber: number; amountUf: string; mode: PrepaymentModeKey }[];
}

interface Golden {
  moneyScale: number;
  rateScale: number;
  cases: {
    name: string;
    input: RawInput;
    expected: Record<string, string | number | null | Record<string, Record<string, string | number>>>;
  }[];
}

const golden: Golden = JSON.parse(
  readFileSync(new URL("../../../golden/mortgage.json", import.meta.url), "utf8"),
) as Golden;

const M = golden.moneyScale;
const R = golden.rateScale;

const inputOf = (raw: RawInput): MortgageInput => ({
  propertyValueUf: money(raw.propertyValueUf),
  downPaymentUf: money(raw.downPaymentUf),
  annualRatePct: money(raw.annualRatePct),
  termYears: raw.termYears,
  rateConvention: raw.rateConvention,
  lifeInsuranceMonthlyPct: money(raw.lifeInsuranceMonthlyPct),
  fireInsuranceMonthlyUf: money(raw.fireInsuranceMonthlyUf),
  originationFeeUf: money(raw.originationFeeUf),
  stampTaxPct: money(raw.stampTaxPct),
  otherUpfrontCostsUf: money(raw.otherUpfrontCostsUf),
  firstPaymentDate: raw.firstPaymentDate,
  prepayments: raw.prepayments.map((p) => ({
    monthNumber: p.monthNumber, amountUf: money(p.amountUf), mode: p.mode,
  })),
});

describe("the mortgage golden vectors", () => {
  it("has cases to check", () => {
    expect(golden.cases.length).toBeGreaterThan(0);
  });

  for (const c of golden.cases) {
    it(c.name, () => {
      const r = simulate(inputOf(c.input));
      const e = c.expected as Record<string, string | number | null>;

      const check = (field: string, actual: string) =>
        expect(`${field} = ${actual}`).toBe(`${field} = ${String(e[field])}`);
      const m = (v: Money) => v.toFixed(M);

      check("loanAmountUf", m(r.loanAmountUf));
      check("monthlyRate", r.monthlyRate.toFixed(R));
      check("basePaymentUf", m(r.basePaymentUf));
      check("firstTotalPaymentUf", m(r.firstTotalPaymentUf));
      check("totalInterestUf", m(r.totalInterestUf));
      check("totalLifeInsuranceUf", m(r.totalLifeInsuranceUf));
      check("totalFireInsuranceUf", m(r.totalFireInsuranceUf));
      check("totalPrepaymentsUf", m(r.totalPrepaymentsUf));
      check("upfrontCostsUf", m(r.upfrontCostsUf));
      check("totalCostUf", m(r.totalCostUf));
      check("scheduleLength", String(r.schedule.length));
      check("effectiveTermMonths", String(r.effectiveTermMonths));
      check("monthsSaved", String(r.monthsSaved));

      if (e.caePct === null) expect(r.caePct).toBeNull();
      else check("caePct", m(r.caePct!));

      const rows = (c.expected as { rows: Record<string, Record<string, string | number>> }).rows;
      for (const [number, row] of Object.entries(rows)) {
        const a = r.schedule[Number(number) - 1]!;
        const checkRow = (field: string, actual: string) =>
          expect(`cuota ${number} / ${field} = ${actual}`)
            .toBe(`cuota ${number} / ${field} = ${String(row[field])}`);

        checkRow("number", String(a.number));
        checkRow("date", a.date);
        checkRow("openingBalanceUf", m(a.openingBalanceUf));
        checkRow("interestUf", m(a.interestUf));
        checkRow("principalUf", m(a.principalUf));
        checkRow("paymentUf", m(a.paymentUf));
        checkRow("lifeInsuranceUf", m(a.lifeInsuranceUf));
        checkRow("fireInsuranceUf", m(a.fireInsuranceUf));
        checkRow("prepaymentUf", m(a.prepaymentUf));
        checkRow("totalOutflowUf", m(a.totalOutflowUf));
        checkRow("closingBalanceUf", m(a.closingBalanceUf));
      }
    });
  }
});
