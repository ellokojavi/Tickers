import type { ComponentChildren } from "preact";
import { useMemo } from "preact/hooks";
import * as Fmt from "../core/format.ts";
import type { Money } from "../domain/money.ts";
import { today as todayIso } from "../domain/dates.ts";
import {
  chartSummary, currentOf, delta, deltaPct, downsample, historyWindow,
} from "../domain/ufEngine.ts";
import { btcUsdToClp } from "../domain/btc.ts";
import { fetchErrorCopy } from "../domain/btc.ts";
import { DataSource, cadenceOf, isCompanion, type Indicator } from "../domain/models.ts";
import type { UfData } from "../data/useUfData.ts";
import type { UsdData } from "../data/useUsdData.ts";
import { useBtcGlance } from "../data/useBtcGlance.ts";
import type { Spot, SpotFailure } from "../data/btcApi.ts";
import { Card, KeyValue, Pill, SectionTitle, Sparkline } from "./components.tsx";
import { hashFor, type TabKey } from "./route.ts";

/**
 * The three indicators at a glance, each a door to the screen that owns it.
 *
 * It exists because the app had no front page: it opened on the UF, so the
 * dollar and bitcoin were things you had to know were there. The cards carry
 * what someone checking in the morning actually wants — the figure, how it
 * moved, and where it came from — and nothing that needs interacting with.
 * Anything you would want to *do* with a number lives one tap away, on the
 * screen that has the converter, the date lookup and the scrubbable chart.
 *
 * Each card is a link rather than a button, so it can be middle-clicked,
 * opened in a tab and copied like any other link, which is the point of the
 * screens having their own addresses at all.
 */

/** Short enough to read as a trend, tall enough to have a shape. */
const GLANCE_HEIGHT = 56;

/** More points than a card is wide cannot be resolved. */
const GLANCE_POINTS = 120;

/** The span each card draws, matching what its own screen opens on. */
const UF_MONTHS = 3;
const USD_MONTHS = 12;

const toneOf = (v: Money): string => (v.cmp(0) > 0 ? "positive" : v.cmp(0) < 0 ? "negative" : "");

const isOk = (r: Spot | SpotFailure | null): r is Spot => r !== null && "usd" in r;

/**
 * A companion indicator is not always in pesos: a rate is a percentage and the
 * copper price is in dollars, so the figure is punctuated by what the source
 * says it is rather than assumed.
 */
const valueOf = (i: Indicator): string => {
  const unit = i.unit.toLowerCase();
  if (unit === "porcentaje") return Fmt.pct1(i.value);
  if (unit === "dólar" || unit === "dolar") return Fmt.usd(i.value);
  return Fmt.clpExact(i.value);
};

/**
 * One indicator, as a card that is entirely a link. The chart inside it takes
 * no pointer events, so a tap anywhere on the card opens the screen rather
 * than landing on a line that cannot report what it was asked.
 */
const IndicatorCard = (
  { to, title, value, secondary, deltas, pills, points, chartLabel, note }: {
    to: TabKey;
    title: string;
    value: string;
    /** A second reading of the same figure, when there is one: bitcoin's pesos. */
    secondary?: string | null;
    deltas: ComponentChildren;
    pills: ComponentChildren;
    points: readonly { readonly value: Money }[];
    chartLabel: string;
    note?: string | null;
  },
) => (
  <Card class="glance">
    <a class="glance-link" href={hashFor(to)}>
      <div class="row">
        <SectionTitle>{title}</SectionTitle>
        <span class="glance-go" aria-hidden="true">›</span>
      </div>
      <p class="glance-value">{value}</p>
      {secondary !== null && secondary !== undefined && (
        <p class="glance-second">{secondary}</p>
      )}
      <div class="row" style={{ justifyContent: "flex-start", gap: 14, marginTop: 4 }}>
        {deltas}
      </div>
      {note !== null && note !== undefined && (
        <p class="muted" style={{ margin: "10px 0 0" }}>{note}</p>
      )}
      {points.length >= 2 && (
        <div style={{ marginTop: 10 }}>
          <Sparkline values={points} height={GLANCE_HEIGHT} guides={false} label={chartLabel} />
        </div>
      )}
      <div class="row" style={{ justifyContent: "flex-start", gap: 8, marginTop: 12 }}>
        {pills}
      </div>
    </a>
  </Card>
);

export const OverviewView = ({ data, usd }: { data: UfData; usd: UsdData }) => {
  const today = todayIso();
  const btc = useBtcGlance();

  // ---------------------------------------------------------------- the UF
  const uf = useMemo(() => currentOf(data.series, today), [data.series, today]);
  const ufPrevious = useMemo(
    () => (uf === null ? null : data.series.filter((v) => v.date < uf.date).at(-1) ?? null),
    [data.series, uf],
  );
  const ufMonthAgo = useMemo(() => {
    const window = historyWindow(data.series, 1, today);
    return window[0] ?? null;
  }, [data.series, today]);

  const ufPoints = useMemo(
    () => downsample(historyWindow(data.series, UF_MONTHS, today), GLANCE_POINTS),
    [data.series, today],
  );
  const ufDaily = uf !== null && ufPrevious !== null ? delta(ufPrevious.value, uf.value) : null;
  const ufMonth = uf !== null && ufMonthAgo !== null ? deltaPct(ufMonthAgo.value, uf.value) : null;

  // ------------------------------------------------------------- the dollar
  const dollar = useMemo(() => currentOf(usd.series, today), [usd.series, today]);
  const dollarPrevious = useMemo(
    () => (dollar === null ? null
      : usd.series.filter((v) => v.date < dollar.date).at(-1) ?? null),
    [usd.series, dollar],
  );
  const dollarPoints = useMemo(
    () => downsample(historyWindow(usd.series, USD_MONTHS, today), GLANCE_POINTS),
    [usd.series, today],
  );
  const dollarDaily = dollar !== null && dollarPrevious !== null
    ? delta(dollarPrevious.value, dollar.value) : null;

  // ------------------------------------------------------------ the bitcoin
  const btcPoints = useMemo(
    () => btc.candles.map((c) => ({ ...c, value: c.usd })),
    [btc.candles],
  );
  const btcSummary = btc.candles.length >= 2
    ? chartSummary(
        btc.candles[0]!.usd, btc.candles.at(-1)!.usd,
        Math.floor((btc.candles.at(-1)!.at - btc.candles[0]!.at) / 86_400_000),
      )
    : null;
  // The dollar the app already has, so the peso figure carries its date rather
  // than implying it is as live as the price above it.
  const btcRate = useMemo(
    () => data.indicators.find((i) => i.code === "dolar") ?? null,
    [data.indicators],
  );
  // What is listed, as opposed to what is fetched: the dollar is asked for
  // because three screens convert through it, and shown here it would be the
  // same figure the card above already carries.
  const companions = useMemo(
    () => data.indicators.filter((i) => isCompanion(i.code)),
    [data.indicators],
  );

  const btcUsd = isOk(btc.spot) ? btc.spot.usd : null;
  const btcClp = btcUsd !== null && btcRate !== null ? btcUsdToClp(btcUsd, btcRate.value) : null;

  return (
    <>
      <IndicatorCard
        to="uf"
        title="UF"
        value={uf === null ? "—" : Fmt.clpExact(uf.value)}
        deltas={<>
          {ufDaily !== null && (
            <span class={toneOf(ufDaily)}>{Fmt.clpSigned(ufDaily)} vs. ayer</span>
          )}
          {ufMonth !== null && (
            <span class={toneOf(ufMonth)}>{Fmt.pctSigned(ufMonth)} en 30 días</span>
          )}
        </>}
        pills={<>
          <Pill official={DataSource[data.source].official}>{DataSource[data.source].label}</Pill>
          {uf !== null && <Pill>Actualizado {Fmt.relativeDay(uf.date, today)}</Pill>}
        </>}
        points={ufPoints}
        chartLabel="Evolución del valor de la UF"
        note={data.stale
          ? "Sin conexión con la fuente. Se muestran los datos incluidos en la app."
          : null}
      />

      <IndicatorCard
        to="dolar"
        title="Dólar observado"
        value={dollar === null ? "—" : Fmt.clpExact(dollar.value)}
        deltas={dollarDaily !== null && (
          <span class={toneOf(dollarDaily)}>
            {Fmt.clpSigned(dollarDaily)} vs. la publicación anterior
          </span>
        )}
        pills={<>
          <Pill official>Dólar observado</Pill>
          {dollar !== null && <Pill>Publicado el {Fmt.dayMonth(dollar.date)}</Pill>}
        </>}
        points={dollarPoints}
        chartLabel="Evolución del dólar observado"
      />

      <IndicatorCard
        to="btc"
        title="Bitcoin"
        value={btcUsd === null ? (btc.spot === null ? "Cargando…" : "—") : Fmt.usd(btcUsd)}
        // Priced in dollars as the market does; the pesos sit under it rather
        // than beside it, carrying the date of the rate that made them.
        secondary={btcClp === null ? null : Fmt.clp(btcClp)}
        deltas={btcSummary !== null && (
          <span class={toneOf(btcSummary.periodPct)}>
            {Fmt.pctSigned(btcSummary.periodPct)} en 30 días
          </span>
        )}
        pills={<>
          {isOk(btc.spot) && <Pill>{btc.spot.source}</Pill>}
          {btcRate !== null && <Pill>Dólar del {Fmt.dayMonth(btcRate.date)}</Pill>}
        </>}
        points={btcPoints}
        chartLabel="Evolución del precio del bitcoin"
        // A market price cannot be bundled, so this is the one card that can
        // arrive with nothing in it. It says why, in the same words the
        // bitcoin screen uses.
        note={btc.spot !== null && !isOk(btc.spot) ? fetchErrorCopy[btc.spot.kind].hint : null}
      />

      {companions.length > 0 && (
        <Card class="glance-others">
          <SectionTitle>Otros indicadores</SectionTitle>
          {companions.map((i) => (
            <KeyValue
              key={i.code}
              label={i.name}
              // Every figure carries its date. These arrive on different
              // clocks — some daily, some monthly and months behind — and
              // undated they all read as today's.
              note={cadenceOf(i.code) === "monthly"
                ? Fmt.monthYear(i.date)
                : Fmt.dayMonth(i.date)}
              value={valueOf(i)}
            />
          ))}
        </Card>
      )}
    </>
  );
};
