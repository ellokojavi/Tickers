import { describe, expect, it } from "vitest";
import { TABS, hashFor, isCanonical, parseHash, type TabKey } from "../route.ts";

/**
 * The pure half of the router: what a hash means and what a screen's hash is.
 * The hook around it is three lines of browser plumbing; these are the rules
 * a stale link, a hand-typed address or a pasted URL actually hit.
 */

describe("reading the address bar", () => {
  it("resolves every screen's own hash back to it", () => {
    for (const tab of TABS) {
      expect(parseHash(hashFor(tab)), `${hashFor(tab)} should be ${tab}`).toBe(tab);
    }
  });

  it("opens the overview when there is no hash at all", () => {
    expect(parseHash("")).toBe("resumen");
    expect(parseHash("#")).toBe("resumen");
    expect(parseHash("#/")).toBe("resumen");
  });

  it("gives the bitcoin screen a readable slug, not its internal name", () => {
    expect(hashFor("btc")).toBe("#/bitcoin");
    expect(parseHash("#/bitcoin")).toBe("btc");
    // The internal key is not a route: nobody should be able to type it.
    expect(parseHash("#/btc")).toBe("resumen");
  });

  it("forgives the ways a hash gets mangled in transit", () => {
    // A link that lost its slash, gained one, or arrived shouting.
    expect(parseHash("#uf")).toBe("uf");
    expect(parseHash("#//uf")).toBe("uf");
    expect(parseHash("#/uf/")).toBe("uf");
    expect(parseHash("#/UF")).toBe("uf");
    expect(parseHash("#/Creditos")).toBe("creditos");
    // Chat apps and mail clients append tracking junk to anything link-shaped.
    expect(parseHash("#/dolar?utm_source=whatsapp")).toBe("dolar");
  });

  it("lands a route that no longer exists on the overview, never on nothing", () => {
    expect(parseHash("#/utm")).toBe("resumen");
    expect(parseHash("#/../../etc/passwd")).toBe("resumen");
    expect(parseHash("#/uf/detalle")).toBe("resumen");
  });

  it("knows when the address bar already says the right thing", () => {
    expect(isCanonical("#/uf", "uf")).toBe(true);
    // These all resolve to uf, but none of them is what the app would write,
    // which is what makes them worth rewriting in place.
    expect(isCanonical("#uf", "uf")).toBe(false);
    expect(isCanonical("#/UF", "uf")).toBe(false);
    expect(isCanonical("", "resumen")).toBe(false);
  });

  it("gives every screen a distinct hash", () => {
    const hashes = TABS.map((t: TabKey) => hashFor(t));
    expect(new Set(hashes).size).toBe(TABS.length);
  });
});
