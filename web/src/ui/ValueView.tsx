import { useMemo, useState } from "preact/hooks";
import * as Fmt from "../core/format.ts";
import { money, type Money } from "../domain/money.ts";
import { isAfter, today as todayIso, type IsoDate } from "../domain/dates.ts";
import {
  annualisedPct, clpToUf, currentOf, delta, deltaPct, downsample, futureOf, historyWindow, ufToClp,
} from "../domain/ufEngine.ts";
import { daysBetween } from "../domain/dates.ts";
import { resolve, SERIES_START, type LookupResult } from "../domain/ufLookup.ts";
import { DataSource } from "../domain/models.ts";
import type { UfData } from "../data/useUfData.ts";
import { Card, Chips, DateField, KeyValue, NumberField, Pill, SectionTitle, Sparkline } from "./components.tsx";
import { ShareIcon } from "./icons.tsx";
import { InstallCard } from "./InstallCard.tsx";

const RANGES = [
  { key: "M1", label: "1M", months: 1 },
  { key: "M3", label: "3M", months: 3 },
  { key: "M6", label: "6M", months: 6 },
  { key: "Y1", label: "1A", months: 12 },
  { key: "Y5", label: "5A", months: 60 },
  { key: "MAX", label: "Máx", months: null },
] as const;
type RangeKey = (typeof RANGES)[number]["key"];

const MAX_CHART_POINTS = 400;
const DETAIL_ROWS = 400;

export const ValueView = ({ data, onAbout }: { data: UfData; onAbout: () => void }) => {
  const today = todayIso();
  const [range, setRange] = useState<RangeKey>("M3");
  const [scrub, setScrub] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [ufText, setUfText] = useState("1");
  const [clpText, setClpText] = useState("");
  const [lookupDate, setLookupDate] = useState<IsoDate>(today);

  const current = useMemo(() => currentOf(data.series, today), [data.series, today]);
  const previous = useMemo(
    () => (current === null ? null
      : data.series.filter((v) => v.date < current.date).at(-1) ?? null),
    [data.series, current],
  );
  const monthAgo = useMemo(() => {
    if (current === null) return null;
    const target = shiftMonth(current.date, -1);
    return data.series.filter((v) => !isAfter(v.date, target)).at(-1) ?? null;
  }, [data.series, current]);

  const months = RANGES.find((r) => r.key === range)!.months;
  const rangeValues = useMemo(
    () => historyWindow(data.series, months, today),
    [data.series, months, today],
  );
  const chartValues = useMemo(() => downsample(rangeValues, MAX_CHART_POINTS), [rangeValues]);
  const future = useMemo(() => futureOf(data.series, today), [data.series, today]);

  const first = rangeValues[0] ?? null;
  const last = rangeValues[rangeValues.length - 1] ?? null;
  const changePct = first !== null && last !== null ? deltaPct(first.value, last.value) : null;
  const annualPct = first !== null && last !== null && daysBetween(first.date, last.date) > 0
    ? annualisedPct(first.value, last.value, daysBetween(first.date, last.date))
    : null;

  const scrubbed = scrub === null ? null : chartValues[scrub] ?? null;
  const rate = current?.value ?? null;

  const lookup: LookupResult = useMemo(() => {
    const exact = data.series.find((v) => v.date === lookupDate) ?? null;
    const nearest = data.series.filter((v) => v.date <= lookupDate).at(-1) ?? null;
    return resolve(lookupDate, exact, nearest, data.lastPublished, today);
  }, [data.series, data.lastPublished, lookupDate, today]);

  const dailyDelta = current !== null && previous !== null ? delta(previous.value, current.value) : null;
  const monthPct = current !== null && monthAgo !== null ? deltaPct(monthAgo.value, current.value) : null;

  return (
    <>
      <Card>
        <div class="row">
          <span class="muted">{current === null ? "Cargando…" : Fmt.longDate(current.date)}</span>
          {current !== null && (
            <button
              type="button"
              class="btn icon"
              aria-label="Compartir el valor de la UF"
              onClick={() => shareToday(current.value, current.date, dailyDelta, monthPct, future, data.source)}
            ><ShareIcon /></button>
          )}
        </div>
        <p class="hero-value">{current === null ? "—" : Fmt.clpExact(current.value)}</p>
        <div class="row" style={{ justifyContent: "flex-start", gap: 14, marginTop: 8 }}>
          {dailyDelta !== null && (
            <span class={toneOf(dailyDelta)}>{Fmt.clpSigned(dailyDelta)} vs. ayer</span>
          )}
          {monthPct !== null && (
            <span class={toneOf(monthPct)}>{Fmt.pctSigned(monthPct)} en 30 días</span>
          )}
        </div>
        <div class="row" style={{ justifyContent: "flex-start", gap: 8, marginTop: 14 }}>
          <Pill official={DataSource[data.source].official} onClick={onAbout}>
            {DataSource[data.source].label}
          </Pill>
          {current !== null && (
            <Pill>Actualizado {Fmt.relativeDay(current.date, today)}</Pill>
          )}
        </div>
        {data.stale && (
          <p class="tiny" style={{ marginTop: 10 }}>
            Sin conexión con la fuente. Se muestran los datos incluidos en la app.
          </p>
        )}
      </Card>

      <Card>
        <SectionTitle>Conversor</SectionTitle>
        <NumberField
          label="UF"
          suffix="UF"
          value={ufText}
          onChange={(raw) => {
            setUfText(raw);
            const parsed = Fmt.parseNumber(raw);
            setClpText(parsed !== null && rate !== null
              ? ufToClp(money(String(parsed)), rate).toString() : "");
          }}
        />
        <NumberField
          label="Pesos"
          suffix="CLP"
          decimals={false}
          value={clpText === "" && rate !== null && ufText === "1"
            ? ufToClp(money(1), rate).toString() : clpText}
          onChange={(raw) => {
            setClpText(raw);
            const parsed = Fmt.parseNumber(raw);
            setUfText(parsed !== null && rate !== null
              ? clpToUf(money(String(parsed)), rate).toString().replace(".", ",") : "");
          }}
        />
      </Card>

      <Card>
        <SectionTitle>Histórico</SectionTitle>
        <Chips
          label="Rango"
          options={RANGES.map((r) => ({ key: r.key, label: r.label }))}
          selected={range}
          onSelect={(key) => { setRange(key); setScrub(null); }}
        />
        <p class="muted" style={{ minHeight: 24, marginTop: 12 }}>
          {scrubbed !== null
            ? `${Fmt.shortDate(scrubbed.date)}   ${Fmt.clpExact(scrubbed.value)}`
            : changePct !== null
              ? <>
                  <span class={toneOf(changePct)}>{Fmt.pctSigned(changePct)} en el período</span>
                  {annualPct !== null && <>
                    {" · "}
                    <span class={toneOf(annualPct)}>{Fmt.pctSigned(annualPct)} anualizado</span>
                  </>}
                </>
              : " "}
        </p>
        <Sparkline values={chartValues} selected={scrub} onScrub={setScrub} />
        <div class="row" style={{ marginTop: 8, alignItems: "flex-start" }}>
          {first !== null && (
            <div><div class="tiny">{Fmt.shortDate(first.date)}</div><div>{Fmt.clpExact(first.value)}</div></div>
          )}
          {last !== null && (
            <div style={{ textAlign: "right" }}>
              <div class="tiny">{Fmt.shortDate(last.date)}</div><div>{Fmt.clpExact(last.value)}</div>
            </div>
          )}
        </div>
      </Card>

      <Card>
        <SectionTitle>Consultar una fecha</SectionTitle>
        <DateField
          label="Fecha"
          value={lookupDate}
          onChange={setLookupDate}
          min={SERIES_START}
          max={data.lastPublished ?? today}
        />
        <LookupAnswer result={lookup} />
        {data.lastPublished !== null && (
          <p class="tiny" style={{ marginTop: 10 }}>
            Puedes consultar entre el {Fmt.shortDate(SERIES_START)} y el {Fmt.shortDate(data.lastPublished)}.
          </p>
        )}
      </Card>

      {future.length > 0 && (
        <Card>
          <SectionTitle>Valores futuros ya publicados</SectionTitle>
          <p class="muted">
            La UF se reajusta a diario entre el 10 de un mes y el 9 del siguiente, según el IPC del
            mes anterior. Estos valores son oficiales, no proyecciones.
          </p>
          <div style={{ marginTop: 10 }}>
            {future.slice(0, 12).map((v) => (
              <KeyValue key={v.date} label={Fmt.shortDate(v.date)} value={Fmt.clpExact(v.value)} />
            ))}
          </div>
        </Card>
      )}

      {data.indicators.length > 0 && (
        <Card>
          <SectionTitle>Otros indicadores</SectionTitle>
          {data.indicators.map((i) => (
            <KeyValue
              key={i.code}
              label={i.name}
              value={i.unit.toLowerCase() === "porcentaje" ? Fmt.pct1(i.value) : Fmt.clpExact(i.value)}
            />
          ))}
        </Card>
      )}

      <div class="row" style={{ padding: "4px 4px 0" }}>
        <SectionTitle>Detalle diario</SectionTitle>
        <button type="button" class="btn text" onClick={() => setDetailOpen(!detailOpen)}>
          {detailOpen ? "Ocultar" : `${Fmt.integer(rangeValues.length)} días`}
        </button>
      </div>
      {detailOpen && (
        <Card>
          {[...rangeValues].reverse().slice(0, DETAIL_ROWS).map((v) => (
            <KeyValue key={v.date} label={Fmt.shortDate(v.date)} value={Fmt.clpExact(v.value)} />
          ))}
          {rangeValues.length > DETAIL_ROWS && (
            <p class="tiny">Se muestran los {Fmt.integer(DETAIL_ROWS)} días más recientes del período.</p>
          )}
        </Card>
      )}

      <InstallCard />

      <div class="footer">
        <p class="tiny">App independiente, sin relación con la CMF, el Banco Central ni el INE.</p>
        <button type="button" class="btn text" onClick={onAbout}>Acerca de y fuentes</button>
      </div>
    </>
  );
};

const LookupAnswer = ({ result }: { result: LookupResult }) => {
  switch (result.kind) {
    case "exact":
      return (
        <>
          <p class="headline">{Fmt.clpExact(result.value.value)}</p>
          {result.isFuture
            ? <Pill official>Valor oficial ya publicado</Pill>
            : <p class="muted">Valor publicado para esa fecha.</p>}
        </>
      );
    case "nearest":
      return (
        <>
          <p class="headline">{Fmt.clpExact(result.value.value)}</p>
          <p class="muted">
            Sin dato para el {Fmt.shortDate(result.requested)}. Se muestra el{" "}
            {Fmt.shortDate(result.value.date)}, el día publicado más cercano anterior.
          </p>
        </>
      );
    case "notPublishedYet":
      return (
        <>
          <p class="headline" style={{ color: "var(--on-surface-variant)" }}>Sin valor</p>
          <p class="muted">
            {result.lastPublished === null
              ? "Todavía no hay valores publicados para esa fecha."
              : `La UF de esa fecha todavía no se publica. El último valor oficial es el del ${Fmt.shortDate(result.lastPublished)}.`}
          </p>
        </>
      );
    case "beforeCoverage":
      return (
        <>
          <p class="headline" style={{ color: "var(--on-surface-variant)" }}>Sin valor</p>
          <p class="muted">La serie diaria de la UF parte el {Fmt.shortDate(result.earliest)}.</p>
        </>
      );
    default:
      return <p class="muted">Buscando…</p>;
  }
};

const toneOf = (v: Money): string => (v.cmp(0) > 0 ? "positive" : v.cmp(0) < 0 ? "negative" : "");

const shiftMonth = (d: IsoDate, months: number): IsoDate => {
  const [y = "0", m = "1", day = "1"] = d.split("-");
  const total = Number(y) * 12 + (Number(m) - 1) + months;
  const ny = Math.floor(total / 12);
  const nm = (total % 12) + 1;
  const lastDay = new Date(Date.UTC(ny, nm, 0)).getUTCDate();
  const nd = Math.min(Number(day), lastDay);
  return `${String(ny).padStart(4, "0")}-${String(nm).padStart(2, "0")}-${String(nd).padStart(2, "0")}`;
};

const shareToday = (
  value: Money, date: IsoDate, dailyDelta: Money | null, monthPct: Money | null,
  future: readonly { date: IsoDate; value: Money }[], source: keyof typeof DataSource,
): void => {
  const deltas = [
    dailyDelta === null ? null : `${Fmt.clpSigned(dailyDelta)} vs. ayer`,
    monthPct === null ? null : `${Fmt.pctSigned(monthPct)} en 30 días`,
  ].filter((s): s is string => s !== null);

  const lines = [
    "*UF de hoy*", Fmt.longDate(date), Fmt.clpExact(value),
    ...(deltas.length > 0 ? ["", deltas.join("  ·  ")] : []),
    ...(future.length > 0
      ? ["", "*Próximos valores ya publicados*",
         ...future.slice(0, 8).map((v) => `${Fmt.shortDate(v.date)}   ${Fmt.clpExact(v.value)}`)]
      : []),
    "", `Fuente: ${DataSource[source].label}`,
  ];
  void share("Valor de la UF", lines.join("\n"));
};

export const share = async (title: string, text: string): Promise<void> => {
  if (typeof navigator.share === "function") {
    try { await navigator.share({ title, text }); return; } catch { /* dismissed */ }
  }
  try { await navigator.clipboard.writeText(text); alert("Copiado al portapapeles"); }
  catch { /* nothing else to try */ }
};
