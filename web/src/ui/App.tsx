import { useEffect, useRef, useState } from "preact/hooks";
import { useUfData } from "../data/useUfData.ts";
import {
  readRailCollapsed, readTheme, writeRailCollapsed, writeTheme, type ThemeMode,
} from "../data/store.ts";
import { OverviewView } from "./OverviewView.tsx";
import { ValueView } from "./ValueView.tsx";
import { InflationView } from "./InflationView.tsx";
import { BtcView } from "./BtcView.tsx";
import { UsdView } from "./UsdView.tsx";
import { useUsdData } from "../data/useUsdData.ts";
import { CreditView } from "./CreditView.tsx";
import { AboutSheet } from "./AboutSheet.tsx";
import { hashFor, useRoute, type TabKey } from "./route.ts";
import {
  BankIcon, BitcoinIcon, CalculatorIcon, ChartIcon, DollarIcon, MenuIcon, MoreIcon,
  OverviewIcon, RefreshIcon, ThemeIcon,
} from "./icons.tsx";

/**
 * Two sections, and they are not equals.
 *
 * The indicators are what the app is opened for, so all four sit in the bar
 * where a thumb can reach them. The calculators are things you go to on
 * purpose, once, and they are behind one more tap: five slots is what a bottom
 * bar can hold and still be hit accurately, and spending four of them on
 * things checked daily is the right way to spend them.
 *
 * A wide screen has no such limit, so the rail shows both sections in full.
 * It is the same navigation with the same two groups, not a different one.
 */
const GROUPS = [
  {
    title: "Indicadores",
    tabs: [
      { key: "resumen", label: "Resumen", Icon: OverviewIcon },
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

const [INDICATORS, TOOLS] = [GROUPS[0].tabs, GROUPS[1].tabs];
type Tab = (typeof GROUPS)[number]["tabs"][number];

const TITLES: Record<TabKey, string> = {
  resumen: "Indicadores",
  uf: "Unidad de Fomento",
  dolar: "Dólar observado",
  btc: "Bitcoin",
  inflacion: "Calculadora de inflación",
  creditos: "Créditos hipotecarios",
};

/** A destination, in the shape both the bar and the rail want. */
const TabLink = (
  { tab, current, extra }: { tab: Tab; current: TabKey; extra?: string },
) => (
  <a
    href={hashFor(tab.key)}
    class={extra}
    aria-current={tab.key === current ? "page" : undefined}
    title={tab.label}
  >
    <span class="tab-icon"><tab.Icon /></span>
    <span class="tab-label">{tab.label}</span>
  </a>
);

/**
 * The tools, behind one tap on a phone.
 *
 * When a tool is the screen you are on, the button becomes that tool: its
 * icon, its name, marked as the current page. Hiding the two behind a generic
 * label is acceptable; leaving someone unable to see where they are is not.
 */
const ToolsMenu = ({ current }: { current: TabKey }) => {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const active = TOOLS.find((t) => t.key === current) ?? null;

  useEffect(() => {
    if (!open) return;
    const away = (e: Event) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false);
    };
    const escape = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("pointerdown", away);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", away);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);

  // Moving to a tool closes the menu; so does moving anywhere else.
  useEffect(() => setOpen(false), [current]);

  const Icon = active?.Icon ?? MoreIcon;

  return (
    <div class="tab-menu" ref={box}>
      {open && (
        <div class="tab-popup" role="menu" aria-label="Herramientas">
          {TOOLS.map((t) => (
            <a
              key={t.key}
              href={hashFor(t.key)}
              role="menuitem"
              aria-current={t.key === current ? "page" : undefined}
            >
              <span class="tab-icon"><t.Icon /></span>
              <span>{t.label}</span>
            </a>
          ))}
        </div>
      )}
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-current={active !== null ? "page" : undefined}
        title={active?.label ?? "Herramientas"}
        onClick={() => setOpen(!open)}
      >
        <span class="tab-icon"><Icon /></span>
        <span class="tab-label">{active?.label ?? "Herramientas"}</span>
      </button>
    </div>
  );
};

export const App = () => {
  const data = useUfData();
  const usd = useUsdData();
  // Navigation is by link, so nothing here has to move the route by hand.
  const [tab] = useRoute();
  const [theme, setTheme] = useState<ThemeMode>(readTheme);
  const [about, setAbout] = useState(false);
  // Only ever consulted by the desktop rail. The bottom tab bar on a phone has
  // nothing to collapse.
  const [railCollapsed, setRailCollapsed] = useState(readRailCollapsed);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    writeTheme(theme);
  }, [theme]);

  // A new screen starts at its own top, the way following a link does
  // anywhere else. Without this, arriving from a card halfway down the
  // overview opens the next screen already scrolled.
  useEffect(() => { window.scrollTo(0, 0); }, [tab]);

  const toggleTheme = () => setTheme(theme === "light" ? "dark" : "light");

  const toggleRail = () => {
    setRailCollapsed(!railCollapsed);
    writeRailCollapsed(!railCollapsed);
  };

  return (
    <div class={`app${railCollapsed ? " rail-collapsed" : ""}`}>
      <h1 class="screen-title">
        {TITLES[tab]}
        {(tab === "resumen" || tab === "uf") && (
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
        {tab === "resumen" && <OverviewView data={data} usd={usd} />}
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

        <div class="tab-group" aria-hidden="true">{GROUPS[0].title}</div>
        {INDICATORS.map((t) => <TabLink key={t.key} tab={t} current={tab} />)}

        {/* The rule between the sections is a horizontal line down the rail and
            a vertical hairline along the bottom bar: the same separator,
            turned with the bar. */}
        <hr class="tab-divider" />

        {/* The same two destinations, twice: a menu where there are five slots
            to spend, and plain items where there is a whole rail. `display:
            none` takes the unused one out of the accessibility tree too, so
            nothing is announced twice. */}
        <ToolsMenu current={tab} />
        <div class="tab-group rail-only" aria-hidden="true">{GROUPS[1].title}</div>
        {TOOLS.map((t) => <TabLink key={t.key} tab={t} current={tab} extra="rail-only" />)}
      </nav>

      {about && <AboutSheet onClose={() => setAbout(false)} />}
    </div>
  );
};
