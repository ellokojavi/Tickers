import { describe, expect, it } from "vitest";
import { COMPANIONS, FETCHED_CODES, cadenceOf, isCompanion } from "../models.ts";

/**
 * Which companion indicators are shown, in what order, and which are asked for
 * without being shown.
 *
 * This is a list of decisions rather than a calculation, and it is pinned for
 * the same reason the chart plan is: it changes by hand, and a hand can change
 * it while meaning to change something else. It was written after exactly
 * that. Removing the dólar observado from the list — correct, since it has its
 * own card — also removed it from the request, and three screens convert
 * through that rate. The bitcoin card silently lost its peso figure, the
 * bitcoin screen lost its peso and UF readings, and the UF converter lost its
 * dollar field. Nothing threw; the figures simply stopped being there.
 */

describe("the companion indicators", () => {
  it("does not list the dólar observado, which has a card of its own", () => {
    expect(isCompanion("dolar")).toBe(false);
  });

  it("still asks for it, because three screens convert through it", () => {
    expect(FETCHED_CODES).toContain("dolar");
  });

  it("asks for everything it lists", () => {
    for (const c of COMPANIONS) {
      expect(FETCHED_CODES, `${c.code} is listed but never fetched`).toContain(c.code);
    }
  });

  it("never shows a series the source has abandoned or the app already has live", () => {
    // Frozen since 2014: listing it would present a dead figure as a current one.
    expect(isCompanion("dolar_intercambio")).toBe(false);
    // Stale here, and the bitcoin card above carries a live price.
    expect(isCompanion("bitcoin")).toBe(false);
    // The UF is the app's whole subject; it is not a footnote to itself.
    expect(isCompanion("uf")).toBe(false);
  });

  it("orders them from the reader's own money outwards", () => {
    expect(COMPANIONS.map((c) => c.code)).toEqual([
      "utm",            // what taxes and fines are owed in
      "tpm",            // what money costs, which is what the credit tool models
      "ipc",            // the inflation the UF carries, and the other tool's subject
      "euro",           // the other currency worth converting
      "libra_cobre",    // what moves the dollar, which moves the rest
      "ivp",            // the UF's forgotten sibling
      "imacec",         // the country, not the reader's pocket
      "tasa_desempleo",
    ]);
  });

  it("dates a monthly figure to its month and a daily one to its day", () => {
    expect(cadenceOf("ipc")).toBe("monthly");
    expect(cadenceOf("utm")).toBe("monthly");
    expect(cadenceOf("imacec")).toBe("monthly");
    expect(cadenceOf("tasa_desempleo")).toBe("monthly");
    expect(cadenceOf("tpm")).toBe("daily");
    expect(cadenceOf("euro")).toBe("daily");
    expect(cadenceOf("libra_cobre")).toBe("daily");
    expect(cadenceOf("ivp")).toBe("daily");
  });

  it("treats an indicator it has never heard of as a daily one", () => {
    // The source can add a key at any time. Guessing "daily" dates it to the
    // day the source stamped, which is the truth as far as the app knows it.
    expect(cadenceOf("no_existe")).toBe("daily");
  });

  it("lists each one once", () => {
    const codes = COMPANIONS.map((c) => c.code);
    expect(new Set(codes).size).toBe(codes.length);
  });
});
