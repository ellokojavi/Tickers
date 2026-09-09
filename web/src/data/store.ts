import { money } from "../domain/money.ts";
import type { MortgageInput, Simulation } from "../domain/models.ts";

/**
 * Saved simulations, in localStorage.
 *
 * Everything stays on the device, which is the same promise the Android app
 * makes. Money is stored as its decimal string, never as a JavaScript number,
 * for the same reason it is never a Double there.
 */
const KEY = "ufchile.simulations.v1";

interface StoredInput {
  propertyValueUf: string; downPaymentUf: string; annualRatePct: string;
  termYears: number; rateConvention: string;
  lifeInsuranceMonthlyPct: string; fireInsuranceMonthlyUf: string;
  originationFeeUf: string; stampTaxPct: string; otherUpfrontCostsUf: string;
  firstPaymentDate: string;
  prepayments: { monthNumber: number; amountUf: string; mode: string }[];
}

interface StoredSimulation {
  id: string; name: string; notes: string;
  input: StoredInput; createdAt: number; updatedAt: number;
}

const encode = (i: MortgageInput): StoredInput => ({
  propertyValueUf: i.propertyValueUf.toString(),
  downPaymentUf: i.downPaymentUf.toString(),
  annualRatePct: i.annualRatePct.toString(),
  termYears: i.termYears,
  rateConvention: i.rateConvention,
  lifeInsuranceMonthlyPct: i.lifeInsuranceMonthlyPct.toString(),
  fireInsuranceMonthlyUf: i.fireInsuranceMonthlyUf.toString(),
  originationFeeUf: i.originationFeeUf.toString(),
  stampTaxPct: i.stampTaxPct.toString(),
  otherUpfrontCostsUf: i.otherUpfrontCostsUf.toString(),
  firstPaymentDate: i.firstPaymentDate,
  prepayments: i.prepayments.map((p) => ({
    monthNumber: p.monthNumber, amountUf: p.amountUf.toString(), mode: p.mode,
  })),
});

const decode = (s: StoredInput): MortgageInput => ({
  propertyValueUf: money(s.propertyValueUf),
  downPaymentUf: money(s.downPaymentUf),
  annualRatePct: money(s.annualRatePct),
  termYears: s.termYears,
  rateConvention: s.rateConvention === "EFFECTIVE_EQUIVALENT"
    ? "EFFECTIVE_EQUIVALENT" : "NOMINAL_DIVIDED",
  lifeInsuranceMonthlyPct: money(s.lifeInsuranceMonthlyPct),
  fireInsuranceMonthlyUf: money(s.fireInsuranceMonthlyUf),
  originationFeeUf: money(s.originationFeeUf),
  stampTaxPct: money(s.stampTaxPct),
  otherUpfrontCostsUf: money(s.otherUpfrontCostsUf),
  firstPaymentDate: s.firstPaymentDate,
  prepayments: s.prepayments.map((p) => ({
    monthNumber: p.monthNumber,
    amountUf: money(p.amountUf),
    mode: p.mode === "REDUCE_PAYMENT" ? "REDUCE_PAYMENT" : "REDUCE_TERM",
  })),
});

const read = (): StoredSimulation[] => {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw === null) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as StoredSimulation[]) : [];
  } catch {
    // A browser with storage blocked must not take the app down with it.
    return [];
  }
};

const write = (rows: StoredSimulation[]): void => {
  try {
    localStorage.setItem(KEY, JSON.stringify(rows));
  } catch {
    /* storage full or blocked: the session still works, it just will not persist */
  }
};

export const listSimulations = (): Simulation[] =>
  read()
    .map((s) => ({ ...s, input: decode(s.input) }))
    .sort((a, b) => b.updatedAt - a.updatedAt);

export const getSimulation = (id: string): Simulation | null =>
  listSimulations().find((s) => s.id === id) ?? null;

export const createSimulation = (name: string, notes: string, input: MortgageInput): string => {
  const now = Date.now();
  const id = `${now}-${Math.random().toString(36).slice(2, 8)}`;
  write([...read(), { id, name, notes, input: encode(input), createdAt: now, updatedAt: now }]);
  return id;
};

export const updateSimulation = (s: Simulation): void => {
  write(read().map((row) => (row.id === s.id
    ? { ...row, name: s.name, notes: s.notes, input: encode(s.input), updatedAt: Date.now() }
    : row)));
};

export const duplicateSimulation = (id: string, newName: string): string | null => {
  const original = getSimulation(id);
  if (original === null) return null;
  return createSimulation(newName, original.notes, original.input);
};

export const deleteSimulation = (id: string): void => {
  write(read().filter((row) => row.id !== id));
};

// ------------------------------------------------------------------ settings

const THEME_KEY = "ufchile.theme.v1";
export type ThemeMode = "system" | "light" | "dark";

export const readTheme = (): ThemeMode => {
  try {
    const v = localStorage.getItem(THEME_KEY);
    return v === "light" || v === "dark" ? v : "system";
  } catch {
    return "system";
  }
};

export const writeTheme = (mode: ThemeMode): void => {
  try { localStorage.setItem(THEME_KEY, mode); } catch { /* ignore */ }
};

const RAIL_KEY = "ufchile.railCollapsed.v1";

export const readRailCollapsed = (): boolean => {
  try { return localStorage.getItem(RAIL_KEY) === "1"; } catch { return false; }
};

export const writeRailCollapsed = (collapsed: boolean): void => {
  try { localStorage.setItem(RAIL_KEY, collapsed ? "1" : "0"); } catch { /* ignore */ }
};
