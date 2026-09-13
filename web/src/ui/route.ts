import { useEffect, useState } from "preact/hooks";

/**
 * One URL per screen.
 *
 * The app used to hold its current tab in a `useState`, which meant it had a
 * single URL: nobody could link anyone to the bitcoin screen, the browser's
 * back button left the app entirely, and an installed PWA always reopened on
 * the same tab. Routing through the hash rather than the path keeps the app a
 * static file — GitHub Pages has no rewrite rule to send unknown paths back to
 * index.html, and the service worker would have to grow one too.
 *
 * The slug is not always the internal key: the bitcoin screen is `btc` in the
 * code, because that is what the domain and the CSS call it, and `#/bitcoin`
 * in the address bar, because that is what a person reads.
 */
export const TABS = ["resumen", "uf", "dolar", "btc", "inflacion", "creditos"] as const;
export type TabKey = (typeof TABS)[number];

/** The overview is the app's front door, so it gets the bare hash. */
const SLUGS: Record<TabKey, string> = {
  resumen: "",
  uf: "uf",
  dolar: "dolar",
  btc: "bitcoin",
  inflacion: "inflacion",
  creditos: "creditos",
};

export const hashFor = (tab: TabKey): string => `#/${SLUGS[tab]}`;

/**
 * Whatever is in the address bar, resolved to a screen that exists.
 *
 * An unknown or malformed hash lands on the overview rather than on nothing:
 * a link that has gone stale should still open the app, which is the same
 * rule as everywhere else here — never a blank screen.
 */
export const parseHash = (hash: string): TabKey => {
  const slug = hash
    .replace(/^#/, "")
    .replace(/^\/+/, "")
    .replace(/\/+$/, "")
    .split(/[?&]/)[0]!
    .toLowerCase();

  if (slug === "") return "resumen";
  return TABS.find((t) => SLUGS[t] === slug) ?? "resumen";
};

/** True when the hash already says exactly what it should for this tab. */
export const isCanonical = (hash: string, tab: TabKey): boolean => hash === hashFor(tab);

/**
 * The current screen, and a way to move to another one.
 *
 * Navigation goes through the hash rather than through state, so every route
 * change lands in the browser's history and the back button does what it
 * looks like it does. A hash that resolved to something other than what was
 * typed is rewritten in place — `#uf` becomes `#/uf` — without pushing an
 * extra entry that the back button would then have to walk through. A visit
 * with no hash at all is left alone, so the plain address stays plain.
 */
export const useRoute = (): readonly [TabKey, (tab: TabKey) => void] => {
  const [tab, setTab] = useState<TabKey>(() => parseHash(window.location.hash));

  useEffect(() => {
    const read = () => setTab(parseHash(window.location.hash));
    window.addEventListener("hashchange", read);
    return () => window.removeEventListener("hashchange", read);
  }, []);

  useEffect(() => {
    const hash = window.location.hash;
    if (hash !== "" && !isCanonical(hash, tab)) {
      window.history.replaceState(null, "", hashFor(tab));
    }
  }, [tab]);

  return [tab, (next: TabKey) => { window.location.hash = hashFor(next); }] as const;
};
