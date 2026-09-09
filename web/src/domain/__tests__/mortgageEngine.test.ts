import { describe, expect, it } from "vitest";
import { money, scale } from "../money.ts";
import { of } from "../dates.ts";
import { financedPct, type MortgageInput, type Prepayment } from "../models.ts";
import { monthlyRate, payment, simulate } from "../mortgageEngine.ts";
import { expectWithin, num } from "../testing.ts";

/**
 * A direct port of MortgageEngineTest from the Android app. The numbers are
 * identical on purpose: if this file passes, the TypeScript engine agrees with
 * the Kotlin one, which is the only way to know the port is faithful.
 */
const input = (o: Partial<{
  property: string; down: string; rate: string; years: number;
  life: string; fire: string; stamp: string; origination: string; other: string;
  prepayments: Prepayment[]; convention: "NOMINAL_DIVIDED" | "EFFECTIVE_EQUIVALENT";
  firstPayment: string;
}> = {}): MortgageInput => ({
  propertyValueUf: money(o.property ?? "5000"),
  downPaymentUf: money(o.down ?? "1000"),
  annualRatePct: money(o.rate ?? "4.5"),
  termYears: o.years ?? 25,
  rateConvention: o.convention ?? "NOMINAL_DIVIDED",
  lifeInsuranceMonthlyPct: money(o.life ?? "0"),
  fireInsuranceMonthlyUf: money(o.fire ?? "0"),
  originationFeeUf: money(o.origination ?? "0"),
  stampTaxPct: money(o.stamp ?? "0"),
  otherUpfrontCostsUf: money(o.other ?? "0"),
  firstPaymentDate: o.firstPayment ?? of(2026, 10, 5),
  prepayments: o.prepayments ?? [],
});

describe("mortgage engine", () => {
  it("french payment matches the analytic formula", () => {
    const principal = 4000;
    const i = 0.045 / 12;
    const n = 300;
    const expected = (principal * i) / (1 - Math.pow(1 + i, -n));

    expectWithin(num(payment(money("4000"), money(i.toFixed(12)), n)), 0.001, expected);
  });

  it("rate conventions differ as expected", () => {
    const nominal = monthlyRate(money("4.5"), "NOMINAL_DIVIDED");
    const effective = monthlyRate(money("4.5"), "EFFECTIVE_EQUIVALENT");

    expectWithin(num(nominal), 1e-9, 0.045 / 12);
    expectWithin(num(effective), 1e-9, Math.pow(1.045, 1 / 12) - 1);
    // The effective conversion is always the cheaper of the two.
    expect(effective.cmp(nominal)).toBeLessThan(0);
  });

  it("schedule fully amortises the loan", () => {
    const r = simulate(input());

    expect(r.schedule).toHaveLength(300);
    expectWithin(num(r.schedule[299]!.closingBalanceUf), 0.0001, 0);
  });

  it("principal payments sum to the loan amount", () => {
    const r = simulate(input());
    const total = r.schedule.reduce((acc, row) => acc.plus(row.principalUf), money(0));

    expect(scale(total, 2).toString()).toBe(scale(r.loanAmountUf, 2).toString());
  });

  it("balance chain is internally consistent", () => {
    const r = simulate(input({ life: "0.03", fire: "0.4" }));

    for (let k = 0; k < r.schedule.length - 1; k++) {
      expect(r.schedule[k + 1]!.openingBalanceUf.toString())
        .toBe(r.schedule[k]!.closingBalanceUf.toString());
    }
    for (const row of r.schedule) {
      expect(row.closingBalanceUf.toString())
        .toBe(row.openingBalanceUf.minus(row.principalUf).minus(row.prepaymentUf).toString());
    }
  });

  it("interest equals balance times monthly rate", () => {
    const r = simulate(input());

    for (const row of r.schedule.slice(0, 24)) {
      expect(row.interestUf.toString())
        .toBe(scale(row.openingBalanceUf.times(r.monthlyRate), 4).toString());
    }
  });

  it("insurance is charged on top of the dividend", () => {
    const r = simulate(input({ life: "0.03", fire: "0.4" }));
    const first = r.schedule[0]!;

    expect(first.totalOutflowUf.toString())
      .toBe(first.paymentUf.plus(first.lifeInsuranceUf).plus(first.fireInsuranceUf).toString());
    // Desgravamen follows the balance, so it shrinks over time.
    expect(r.schedule[r.schedule.length - 1]!.lifeInsuranceUf.cmp(first.lifeInsuranceUf))
      .toBeLessThan(0);
  });

  it("zero rate splits the principal evenly", () => {
    const r = simulate(input({ rate: "0", years: 10 }));

    expectWithin(num(r.totalInterestUf), 0.0001, 0);
    expectWithin(num(r.basePaymentUf), 0.001, 4000 / 120);
  });

  it("prepayment that reduces term shortens the schedule", () => {
    const base = simulate(input());
    const withPrepay = simulate(input({
      prepayments: [{ monthNumber: 12, amountUf: money("500"), mode: "REDUCE_TERM" }],
    }));

    expect(withPrepay.schedule.length).toBeLessThan(base.schedule.length);
    expect(withPrepay.monthsSaved).toBeGreaterThan(0);
    expect(withPrepay.totalInterestUf.cmp(base.totalInterestUf)).toBeLessThan(0);
    expectWithin(num(withPrepay.schedule[withPrepay.schedule.length - 1]!.closingBalanceUf), 0.0001, 0);
  });

  it("prepayment that reduces payment keeps the term", () => {
    const base = simulate(input());
    const withPrepay = simulate(input({
      prepayments: [{ monthNumber: 12, amountUf: money("500"), mode: "REDUCE_PAYMENT" }],
    }));

    expect(withPrepay.schedule.length).toBe(base.schedule.length);
    expect(withPrepay.schedule[20]!.paymentUf.cmp(withPrepay.schedule[5]!.paymentUf)).toBeLessThan(0);
    expectWithin(num(withPrepay.schedule[withPrepay.schedule.length - 1]!.closingBalanceUf), 0.0001, 0);
  });

  it("a prepayment cannot exceed the outstanding balance", () => {
    const r = simulate(input({
      prepayments: [{ monthNumber: 2, amountUf: money("999999"), mode: "REDUCE_TERM" }],
    }));

    expect(r.schedule).toHaveLength(2);
    expectWithin(num(r.schedule[1]!.closingBalanceUf), 0.0001, 0);
    expect(r.totalPrepaymentsUf.cmp(money("4000"))).toBeLessThan(0);
  });

  it("CAE without costs equals the effective annual rate", () => {
    const r = simulate(input());
    const expected = (Math.pow(1 + 0.045 / 12, 12) - 1) * 100;

    expect(r.caePct).not.toBeNull();
    expectWithin(num(r.caePct!), 0.02, expected);
  });

  it("CAE rises once fees and insurance are added", () => {
    const bare = simulate(input());
    const loaded = simulate(input({ life: "0.03", fire: "0.4", stamp: "0.8", other: "30" }));

    expect(loaded.caePct!.cmp(bare.caePct!)).toBeGreaterThan(0);
  });

  it("upfront costs are computed off the loan amount", () => {
    const r = simulate(input({ stamp: "0.8", origination: "5", other: "30" }));

    // 4000 UF * 0.8% = 32 UF, plus 5 and 30.
    expectWithin(num(r.upfrontCostsUf), 0.0001, 67);
  });

  it("financed percentage reflects the down payment", () => {
    expectWithin(num(financedPct(input())), 0.001, 80);
  });

  it("total cost adds every outflow plus upfront costs", () => {
    const r = simulate(input({ life: "0.03", fire: "0.4", stamp: "0.8" }));
    const sum = r.schedule.reduce((acc, row) => acc.plus(row.totalOutflowUf), money(0));

    expect(scale(r.totalCostUf, 2).toString()).toBe(scale(sum.plus(r.upfrontCostsUf), 2).toString());
  });

  it("a longer term lowers the payment and raises total interest", () => {
    const short = simulate(input({ years: 15 }));
    const long = simulate(input({ years: 30 }));

    expect(long.basePaymentUf.cmp(short.basePaymentUf)).toBeLessThan(0);
    expect(long.totalInterestUf.cmp(short.totalInterestUf)).toBeGreaterThan(0);
  });

  // ---------------------------------------------------------------- due dates

  it("the first instalment falls on the chosen date", () => {
    const r = simulate(input({ firstPayment: of(2027, 3, 20) }));

    expect(r.schedule[0]!.date).toBe(of(2027, 3, 20));
  });

  it("instalments advance one month at a time", () => {
    const r = simulate(input({ firstPayment: of(2026, 10, 5) }));

    expect(r.schedule[1]!.date).toBe(of(2026, 11, 5));
    expect(r.schedule[11]!.date).toBe(of(2027, 9, 5));
    expect(r.schedule[12]!.date).toBe(of(2027, 10, 5));
    expect(r.schedule[r.schedule.length - 1]!.date).toBe(of(2051, 9, 5));
  });

  it("a month-end due date clamps to shorter months", () => {
    const r = simulate(input({ firstPayment: of(2026, 1, 31) }));

    expect(r.schedule[0]!.date).toBe(of(2026, 1, 31));
    expect(r.schedule[1]!.date).toBe(of(2026, 2, 28));
    expect(r.schedule[2]!.date).toBe(of(2026, 3, 31));
    expect(r.schedule[3]!.date).toBe(of(2026, 4, 30));
  });

  it("the due date does not affect any amount", () => {
    const a = simulate(input({ firstPayment: of(2026, 10, 5) }));
    const b = simulate(input({ firstPayment: of(2031, 2, 17) }));

    expect(b.basePaymentUf.toString()).toBe(a.basePaymentUf.toString());
    expect(b.totalCostUf.toString()).toBe(a.totalCostUf.toString());
    expect(b.caePct!.toString()).toBe(a.caePct!.toString());
  });
});
