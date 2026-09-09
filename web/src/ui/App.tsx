import { useEffect, useState } from "preact/hooks";
import { useUfData } from "../data/useUfData.ts";
import {
  readRailCollapsed, readTheme, writeRailCollapsed, writeTheme, type ThemeMode,
} from "../data/store.ts";
import { ValueView } from "./ValueView.tsx";
import { InflationView } from "./InflationView.tsx";
import { BtcView } from "./BtcView.tsx";
import { CreditView } from "./CreditView.tsx";
import { AboutSheet } from "./AboutSheet.tsx";
import {
  BankIcon, BitcoinIcon, CalculatorIcon, ChartIcon, MenuIcon, RefreshIcon, ThemeIcon,
} from "./icons.tsx";

// Bitcoin sits next to the UF because both answer "what is this worth today",
// and it keeps its own tab rather than joining the UF screen because its
// content is a different shape: a price that moves by the second, an intraday
// chart, and a market rather than a published figure. When the dollar arrives
// it will join the UF tab instead, since those two are the same shape.
const TABS = [
  { key: "uf", label: "Valor UF", Icon: ChartIcon },
  { key: "btc", label: "Bitcoin", Icon: BitcoinIcon },
  { key: "inflacion", label: "Inflación", Icon: CalculatorIcon },
  { key: "creditos", label: "Créditos", Icon: BankIcon },
] as const;
type TabKey = (typeof TABS)[number]["key"];

const TITLES: Record<TabKey, string> = {
  uf: "Unidad de Fomento",
  btc: "Bitcoin",
  inflacion: "Calculadora de inflación",
  creditos: "Créditos hipotecarios",
};

export const App = () => {
  const data = useUfData();
  const [tab, setTab] = useState<TabKey>("uf");
  const [theme, setTheme] = useState<ThemeMode>(readTheme);
  const [about, setAbout] = useState(false);
  // Only ever consulted by the desktop rail. The bottom tab bar on a phone has
  // nothing to collapse.
  const [railCollapsed, setRailCollapsed] = useState(readRailCollapsed);

  useEffect(() => {
    if (theme === "system") document.documentElement.removeAttribute("data-theme");
    else document.documentElement.setAttribute("data-theme", theme);
    writeTheme(theme);
  }, [theme]);

  const cycleTheme = () =>
    setTheme(theme === "system" ? "light" : theme === "light" ? "dark" : "system");

  const toggleRail = () => {
    setRailCollapsed(!railCollapsed);
    writeRailCollapsed(!railCollapsed);
  };

  return (
    <div class={`app${railCollapsed ? " rail-collapsed" : ""}`}>
      <h1 class="screen-title">
        {TITLES[tab]}
        {tab === "uf" && (
          <span>
            <button type="button" class="btn icon" aria-label="Cambiar tema" onClick={cycleTheme}>
              <ThemeIcon mode={theme} />
            </button>
            <button type="button" class="btn icon" aria-label="Actualizar" onClick={data.refresh}>
              <RefreshIcon />
            </button>
          </span>
        )}
      </h1>

      {/* The class carries the tab through to CSS: on a wide screen each view
          wants a different arrangement of the same cards. */}
      <main class={`view view-${tab}`}>
        {tab === "uf" && <ValueView data={data} onAbout={() => setAbout(true)} />}
        {tab === "btc" && <BtcView data={data} />}
        {tab === "inflacion" && <InflationView data={data} />}
        {tab === "creditos" && <CreditView data={data} />}
      </main>

      <nav class="tabs">
        {/* Only ever seen at the top of the desktop rail. A bottom tab bar on a
            phone has no room for a wordmark and does not need one. */}
        <div class="rail-head">
          <button
            type="button"
            class="btn icon rail-toggle"
            aria-label={railCollapsed ? "Expandir el menú" : "Colapsar el menú"}
            aria-expanded={!railCollapsed}
            title={railCollapsed ? "Expandir el menú" : "Colapsar el menú"}
            onClick={toggleRail}
          ><MenuIcon /></button>
          <span class="rail-brand">Tickers</span>
        </div>
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            aria-current={t.key === tab ? "page" : undefined}
            title={t.label}
            onClick={() => setTab(t.key)}
          >
            <span class="tab-icon"><t.Icon /></span>
            <span class="tab-label">{t.label}</span>
          </button>
        ))}
      </nav>

      {about && <AboutSheet onClose={() => setAbout(false)} />}
    </div>
  );
};
