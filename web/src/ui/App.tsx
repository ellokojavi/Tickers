import { Fragment } from "preact";
import { useEffect, useState } from "preact/hooks";
import { useUfData } from "../data/useUfData.ts";
import {
  readRailCollapsed, readTheme, writeRailCollapsed, writeTheme, type ThemeMode,
} from "../data/store.ts";
import { ValueView } from "./ValueView.tsx";
import { InflationView } from "./InflationView.tsx";
import { BtcView } from "./BtcView.tsx";
import { UsdView } from "./UsdView.tsx";
import { useUsdData } from "../data/useUsdData.ts";
import { CreditView } from "./CreditView.tsx";
import { AboutSheet } from "./AboutSheet.tsx";
import {
  BankIcon, BitcoinIcon, CalculatorIcon, ChartIcon, DollarIcon, MenuIcon, RefreshIcon, ThemeIcon,
} from "./icons.tsx";

// Two sections: the market figures the app reports, then the calculators it
// started as. Five is the most a bottom bar can hold and still be tapped
// accurately, so this is the ceiling: anything further has to go inside one
// of these rather than beside them.
const GROUPS = [
  {
    title: "Indicadores",
    tabs: [
      { key: "uf", label: "UF", Icon: ChartIcon },
      { key: "dolar", label: "Dólar", Icon: DollarIcon },
      { key: "btc", label: "Bitcoin", Icon: BitcoinIcon },
    ],
  },
  {
    title: "Herramientas",
    tabs: [
      { key: "inflacion", label: "Inflación", Icon: CalculatorIcon },
      { key: "creditos", label: "Créditos", Icon: BankIcon },
    ],
  },
] as const;
type TabKey = (typeof GROUPS)[number]["tabs"][number]["key"];

const TITLES: Record<TabKey, string> = {
  uf: "Unidad de Fomento",
  dolar: "Dólar observado",
  btc: "Bitcoin",
  inflacion: "Calculadora de inflación",
  creditos: "Créditos hipotecarios",
};

export const App = () => {
  const data = useUfData();
  const usd = useUsdData();
  const [tab, setTab] = useState<TabKey>("uf");
  const [theme, setTheme] = useState<ThemeMode>(readTheme);
  const [about, setAbout] = useState(false);
  // Only ever consulted by the desktop rail. The bottom tab bar on a phone has
  // nothing to collapse.
  const [railCollapsed, setRailCollapsed] = useState(readRailCollapsed);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    writeTheme(theme);
  }, [theme]);

  const toggleTheme = () => setTheme(theme === "light" ? "dark" : "light");

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
            <button type="button" class="btn icon" aria-label="Cambiar tema" onClick={toggleTheme}>
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
        {tab === "dolar" && <UsdView data={usd} />}
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
        {GROUPS.map((g, i) => (
          <Fragment key={g.title}>
            {/* The rule between the sections is a horizontal line down the
                rail and a vertical hairline along the bottom bar: the same
                separator, turned with the bar. The heading only fits on the
                rail, and only while it is wide enough to show labels. */}
            {i > 0 && <hr class="tab-divider" />}
            <div class="tab-group" aria-hidden="true">{g.title}</div>
            {g.tabs.map((t) => (
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
          </Fragment>
        ))}
      </nav>

      {about && <AboutSheet onClose={() => setAbout(false)} />}
    </div>
  );
};
