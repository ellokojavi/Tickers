import { useMemo, useState } from "preact/hooks";
import * as Fmt from "../core/format.ts";
import { money } from "../domain/money.ts";
import { today as todayIso, daysBetween } from "../domain/dates.ts";
import {
  chartSummary, currentOf, delta, downsample, historyWindow,
} from "../domain/ufEngine.ts";
import { clpToUsd, usdToClp } from "../domain/fx.ts";
import type { UsdData } from "../data/useUsdData.ts";
import { Card, NumberField, Pill, SectionTitle } from "./components.tsx";
import { HistoryCard } from "./HistoryCard.tsx";
import { ShareButton } from "./share.tsx";

/** Same ranges as the UF chart: the two series are read the same way. */
const RANGES = [
  { key: "1M", label: "1M", months: 1 },
  { key: "3M", label: "3M", months: 3 },
  { key: "6M", label: "6M", months: 6 },
  { key: "1A", label: "1A", months: 12 },
  { key: "5A", label: "5A", months: 60 },
  { key: "MAX", label: "Máx", months: null },
] as const;
type RangeKey = (typeof RANGES)[number]["key"];

/** More points than this cannot be resolved on a phone-width chart. */
const MAX_CHART_POINTS = 400;

export const UsdView = ({ data }: { data: UsdData }) => {
  const [range, setRange] = useState<RangeKey>("1A");
  const [scrub, setScrub] = useState<number | null>(null);
  const [clpText, setClpText] = useState("");
  const [usdText, setUsdText] = useState("1");

  const today = todayIso();
  const current = useMemo(() => currentOf(data.series, today), [data.series, today]);
  const previous = useMemo(
    () => (current === null ? null
      : data.series.filter((v) => v.date < current.date).at(-1) ?? null),
    [data.series, current],
  );

  const months = RANGES.find((r) => r.key === range)!.months;
  const windowed = useMemo(
    () => historyWindow(data.series, months, today),
    [data.series, months, today],
  );
  const points = useMemo(() => downsample(windowed, MAX_CHART_POINTS), [windowed]);

  const rate = current?.value ?? null;
  const dailyDelta = current !== null && previous !== null ? delta(previous.value, current.value) : null;
  // Read off the full window rather than the thinned one.
  const summary = windowed.length >= 2
    ? chartSummary(
        windowed[0]!.value, windowed.at(-1)!.value,
        daysBetween(windowed[0]!.date, windowed.at(-1)!.date),
      )
    : null;

  // One dollar converted, so the card answers before anyone types in it.
  const clpShown = clpText === "" && rate !== null && usdText === "1"
    ? usdToClp(money(1), rate).toString() : clpText;

  const setConverter = (field: "usd" | "clp", raw: string) => {
    if (field === "usd") setUsdText(raw); else setClpText(raw);
    const parsed = Fmt.parseNumber(raw);
    if (parsed === null || rate === null) {
      if (field !== "usd") setUsdText("");
      if (field !== "clp") setClpText("");
      return;
    }
    const amount = money(String(parsed));
    if (field === "usd") setClpText(usdToClp(amount, rate).toString());
    else setUsdText(clpToUsd(amount, rate).toString().replace(".", ","));
  };

  return (
    <>
      <Card>
        <div class="row">
          <span class="muted">
            {current === null
              ? (data.loading ? "Cargando…" : "Sin datos")
              : Fmt.longDate(current.date)}
          </span>
          {current !== null && (
            <ShareButton
              what="el valor del dólar"
              title="Dólar observado"
              text={shareToday(current.date, current.value, dailyDelta)}
            />
          )}
        </div>
        <p class="hero-value">{current === null ? "—" : Fmt.clpExact(current.value)}</p>
        {dailyDelta !== null && (
          <div class="row" style={{ justifyContent: "flex-start", gap: 14, marginTop: 8 }}>
            <span class={dailyDelta.cmp(0) >= 0 ? "positive" : "negative"}>
              {Fmt.clpSigned(dailyDelta)} vs. la publicación anterior
            </span>
          </div>
        )}
        <div class="row" style={{ justifyContent: "flex-start", gap: 8, marginTop: 14 }}>
          <Pill official>Dólar observado</Pill>
          <Pill>Banco Central</Pill>
        </div>
      </Card>

      <Card>
        <SectionTitle>Conversor</SectionTitle>
        <NumberField
          // The date belongs to the rate, which is what the other field is
          // derived from.
          label={current === null ? "Dólares" : `Dólares (al ${Fmt.dayMonth(current.date)})`}
          suffix="USD"
          value={usdText}
          onChange={(raw) => setConverter("usd", raw)}
        />
        <NumberField
          label="Pesos"
          suffix="CLP"
          decimals={false}
          value={clpShown}
          onChange={(raw) => setConverter("clp", raw)}
          action={clpShown === "" ? undefined : (
            <span class="field-action">
              <ShareButton
                what="el valor en pesos"
                title="Dólar observado"
                text={converterShare(usdText, clpShown)}
              />
            </span>
          )}
        />
      </Card>

      <HistoryCard
        ranges={RANGES}
        range={range}
        onRange={setRange}
        points={points}
        stamp={(v) => Fmt.shortDate(v.date)}
        amount={(v) => Fmt.clpExact(v.value)}
        summary={summary}
        scrub={scrub}
        onScrub={setScrub}
        loading={data.loading}
        chartLabel="Evolución del dólar observado"
      />

      <div class="footer">
        <p class="tiny">
          El dólar observado se publica cada día hábil y refleja las
          transacciones del día hábil anterior. Los fines de semana y feriados no
          tienen publicación, y esos días no se inventan: se muestra el último
          valor publicado, con su fecha.
        </p>
      </div>
    </>
  );
};

const shareToday = (
  date: string, value: import("../domain/money.ts").Money,
  dailyDelta: import("../domain/money.ts").Money | null,
): string => [
  "*Dólar observado*",
  Fmt.longDate(date),
  Fmt.clpExact(value),
  ...(dailyDelta === null ? [] : ["", `${Fmt.clpSigned(dailyDelta)} vs. la publicación anterior`]),
  "",
  "Fuente: Banco Central de Chile",
].join("\n");

/**
 * What the copy button sends. Deliberately one line: it is meant to be pasted
 * into a conversation, not read as a report.
 */
const converterShare = (usdText: string, clp: string): string => {
  const amount = Fmt.parseNumber(usdText);
  const head = amount === 1 ? "Dólar de hoy" : `${Fmt.usd(money(String(amount ?? 0)))}`;
  return `${head}, CLP${Fmt.clp(money(clp === "" ? "0" : clp))}`;
};
