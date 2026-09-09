import { expect } from "vitest";
import type { Money } from "./money.ts";

/** Truth's isWithin(t).of(v), so the ported assertions read like the originals. */
export const expectWithin = (actual: number, tolerance: number, expected: number): void => {
  expect(Math.abs(actual - expected)).toBeLessThanOrEqual(tolerance);
};

export const num = (v: Money): number => Number(v.toString());
