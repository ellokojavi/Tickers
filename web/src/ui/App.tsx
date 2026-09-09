import { useEffect, useState } from "preact/hooks";
import { useUfData } from "../data/useUfData.ts";
import { readTheme, writeTheme, type ThemeMode } from "../data/store.ts";
import { ValueView } from "./ValueView.tsx";
import { InflationView } from "./InflationView.tsx";
import { CreditView } from "./CreditView.tsx";
import { AboutSheet } from "./AboutSheet.tsx";
import { BankIcon, CalculatorIcon, ChartIcon, RefreshIcon, ThemeIcon } from "./icons.tsx";

const TABS = [
  { key: "uf", label: "Valor UF", Icon: ChartIcon },
  { key: "inflacion", label: "Inflación", Icon: CalculatorIcon },
  { key: "creditos", label: "Créditos", Icon: BankIcon },
] as const;
type TabKey = (typeof TABS)[number]["key"];

const TITLES: Record<TabKey, string> = {
  uf: "Unidad de Fomento",
  inflacion: "Calculadora de inflación",
  creditos: "Créditos hipotecarios",
};

export const App = () => {
  const data = useUfData();
  const [tab, setTab] = useState<TabKey>("uf");
  const [theme, setTheme] = useState<ThemeMode>(readTheme);
  const [about, setAbout] = useState(false);

  useEffect(() => {
    if (theme === "system") document.documentElement.removeAttribute("data-theme");
    else document.documentElement.setAttribute("data-theme", theme);
    writeTheme(theme);
  }, [theme]);

  const cycleTheme = () =>
    setTheme(theme === "system" ? "light" : theme === "light" ? "dark" : "system");

  return (
    <div class="app">
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

      {tab === "uf" && <ValueView data={data} onAbout={() => setAbout(true)} />}
      {tab === "inflacion" && <InflationView data={data} />}
      {tab === "creditos" && <CreditView data={data} />}

      <nav class="tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            aria-current={t.key === tab ? "page" : undefined}
            onClick={() => setTab(t.key)}
          >
            <span class="tab-icon"><t.Icon /></span>
            {t.label}
          </button>
        ))}
      </nav>

      {about && <AboutSheet onClose={() => setAbout(false)} />}
    </div>
  );
};
