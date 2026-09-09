import { describe, expect, it } from "vitest";
import { money } from "../../domain/money.ts";
import { of } from "../../domain/dates.ts";
import * as Fmt from "../format.ts";

describe("es-CL formatting", () => {
  it("pesos use Chilean separators", () => {
    expect(Fmt.clp(money("40880.36"))).toBe("$40.880");
    expect(Fmt.clpExact(money("40880.36"))).toBe("$40.880,36");
    expect(Fmt.clp(money("1234567"))).toBe("$1.234.567");
  });

  it("uf keeps two decimals and four in tables", () => {
    expect(Fmt.uf(money("22.2331"))).toBe("22,23 UF");
    expect(Fmt.uf4(money("22.2331"))).toBe("22,2331 UF");
  });

  it("signed values carry an explicit plus", () => {
    expect(Fmt.pctSigned(money("3.25"))).toBe("+3,25%");
    expect(Fmt.pctSigned(money("-3.25"))).toBe("-3,25%");
    expect(Fmt.pctSigned(money("0"))).toBe("0,00%");
    expect(Fmt.clpSigned(money("1.32"))).toBe("+$1,32");
    expect(Fmt.clpSigned(money("-1.32"))).toBe("-$1,32");
  });

  it("counts are grouped like any other figure", () => {
    expect(Fmt.integer(0)).toBe("0");
    expect(Fmt.integer(480)).toBe("480");
    expect(Fmt.integer(13_396)).toBe("13.396");
    expect(Fmt.integer(17_937)).toBe("17.937");
    expect(Fmt.integer(-1500)).toBe("-1.500");
  });

  it("a span reads in the unit that suits its length", () => {
    expect(Fmt.period(0)).toBe("0 días");
    expect(Fmt.period(45)).toBe("45 días");
    expect(Fmt.period(365)).toBe("365 días (12,0 meses)");
    expect(Fmt.period(13_397)).toBe("13.397 días (36,7 años)");
  });

  it("dates render in Spanish, day first", () => {
    const d = of(2026, 9, 8);

    expect(Fmt.shortDate(d)).toBe("08-09-2026");
    expect(Fmt.longDate(d)).toBe("8 de septiembre de 2026");
  });

  it("relative days read naturally", () => {
    const day = of(2026, 9, 8);

    expect(Fmt.relativeDay(day, day)).toBe("hoy");
    expect(Fmt.relativeDay(of(2026, 9, 7), day)).toBe("ayer");
    expect(Fmt.relativeDay(of(2026, 9, 4), day)).toBe("hace 4 días");
    expect(Fmt.relativeDay(of(2026, 9, 9), day)).toBe("mañana");
    expect(Fmt.relativeDay(of(2026, 9, 11), day)).toBe("en 3 días");
  });

  it("parses Chilean formatted input", () => {
    expect(Fmt.parseNumber("40.880,36")).toBe(40880.36);
    expect(Fmt.parseNumber("$1.000")).toBe(1000);
    expect(Fmt.parseNumber("4,5")).toBe(4.5);
    expect(Fmt.parseNumber("1.234 UF")).toBe(1234);
    expect(Fmt.parseNumber("-2,5")).toBe(-2.5);
  });

  it("rejects input that is not a number", () => {
    expect(Fmt.parseNumber("")).toBeNull();
    expect(Fmt.parseNumber("   ")).toBeNull();
    expect(Fmt.parseNumber("abc")).toBeNull();
  });

  /** The same separators the Android app writes, so shared text matches. */
  it("formatting and parsing round trip", () => {
    expect(Fmt.parseNumber(Fmt.clpExact(money("1234567.89")))).toBe(1234567.89);
  });
});
