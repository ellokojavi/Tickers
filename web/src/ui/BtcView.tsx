import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import * as Fmt from "../core/format.ts";
import { money, type Money } from "../domain/money.ts";
import { deltaPct } from "../domain/ufEngine.ts";
import { currentOf } from "../domain/ufEngine.ts";
import { today as todayIso } from "../domain/dates.ts";
import {
  HORIZONS, btcToClp, btcUsdToClp, btcUsdToUf, chartPlan, clpToBtc,
  fetchErrorCopy, horizonLabel, stampKind, type Horizon,
} from "../domain/btc.ts";
import { fetchCandles, fetchSpot, type Candle, type Spot, type SpotFailure } from "../data/btcApi.ts";
import type { UfData } from "../data/useUfData.ts";
import { Card, Chips, KeyValue, NumberField, Pill, SectionTitle, Sparkline } from "./components.tsx";
import { ShareButton } from "./share.tsx";

/** How long a spot price is shown as current before it is called stale. */
const FRESH_MS = 60_000;

const isOk = (r: Spot | SpotFailure | null): r is Spot => r !== null && "usd" in r;

export const BtcView = ({ data }: { data: UfData }) => {
  const [spot, setSpot] = useState<Spot | SpotFailure | null>(null);
  const [horizon, setHorizon] = useState<Horizon>("D30");
  const [candles, setCandles] = useState<readonly Candle[]>([]);
  const [scrub, setScrub] = useState<number | null>(null);
  const [btcText, setBtcText] = useState("1");
  const [clpText, setClpText] = useState("");
  const [now, setNow] = useState(Date.now());

  // The dollar the app already has. It is the Banco Central's observed rate,
  // published once a business day, so the peso figures below carry its date
  // rather than implying they are as live as the bitcoin price.
  const dollar = useMemo(
    () => data.indicators.find((i) => i.code === "dolar") ?? null,
    [data.indicators],
  );
  const uf = useMemo(
    () => currentOf(data.series, todayIso())?.value ?? null,
    [data.series],
  );

  const refresh = () => { void fetchSpot().then(setSpot); };

  useEffect(() => {
    refresh();
    const tick = window.setInterval(refresh, 30_000);
    const clock = window.setInterval(() => setNow(Date.now()), 5_000);
    return () => { window.clearInterval(tick); window.clearInterval(clock); };
  }, []);

  // A horizon change replaces the series; an answer that arrives after the
  // user has moved on is dropped rather than drawn over the new one.
  const wanted = useRef<Horizon>(horizon);
  useEffect(() => {
    wanted.current = horizon;
    setCandles([]);
    setScrub(null);
    void fetchCandles(horizon)
      .then((c) => { if (wanted.current === horizon) setCandles(c); })
      .catch(() => { if (wanted.current === horizon) setCandles([]); });
    const id = window.setInterval(() => {
      void fetchCandles(horizon)
        .then((c) => { if (wanted.current === horizon) setCandles(c); })
        .catch(() => {});
    }, chartPlan(horizon).refreshMs);
    return () => window.clearInterval(id);
  }, [horizon]);

  const usd = isOk(spot) ? spot.usd : null;
  const shown = scrub !== null && candles[scrub] !== undefined ? candles[scrub]!.usd : usd;

  const spanPct = candles.length >= 2
    ? deltaPct(candles[0]!.usd, candles.at(-1)!.usd)
    : null;

  const clp = shown !== null && dollar !== null ? btcUsdToClp(shown, dollar.value) : null;
  const inUf = shown !== null && dollar !== null && uf !== null
    ? btcUsdToUf(shown, dollar.value, uf) : null;

  const stale = isOk(spot) && now - spot.at > FRESH_MS;

  return (
    <>
      <Card>
        <div class="row">
          <span class="muted">
            {spot === null ? "Cargando…"
              : isOk(spot) ? (stale ? `Hace ${Math.round((now - spot.at) / 1000)} s` : "Ahora")
              : fetchErrorCopy[spot.kind].headline}
          </span>
          {usd !== null && (
            <ShareButton
              what="el precio del bitcoin"
              title="Precio del bitcoin"
              text={shareText(usd, clp, inUf, dollar?.date ?? null, isOk(spot) ? spot.source : "")}
            />
          )}
        </div>

        {spot !== null && !isOk(spot) ? (
          <p class="muted" style={{ margin: "10px 0 0" }}>{fetchErrorCopy[spot.kind].hint}</p>
        ) : (
          <>
            <p class="hero-value">{shown === null ? "—" : Fmt.usd(shown)}</p>
            {clp !== null && (
              <p class="display" style={{ fontSize: 24, margin: "2px 0 0" }}>{Fmt.clp(clp)}</p>
            )}
            <div class="row" style={{ justifyContent: "flex-start", gap: 14, marginTop: 8 }}>
              {inUf !== null && <span class="muted">{Fmt.uf(inUf)}</span>}
              {spanPct !== null && (
                <span class={spanPct.cmp(0) >= 0 ? "positive" : "negative"}>
                  {Fmt.pctSigned(spanPct)} en {horizonLabel[horizon]}
                </span>
              )}
            </div>
            <div class="row" style={{ justifyContent: "flex-start", gap: 8, marginTop: 14 }}>
              {isOk(spot) && <Pill>{spot.source}</Pill>}
              {dollar !== null && (
                <Pill>Dólar del {Fmt.dayMonth(dollar.date)}</Pill>
              )}
            </div>
          </>
        )}
      </Card>

      <Card>
        <SectionTitle>Histórico</SectionTitle>
        <Chips
          label="Rango"
          options={HORIZONS.map((h) => ({ key: h, label: horizonLabel[h] }))}
          selected={horizon}
          onSelect={setHorizon}
        />
        {candles.length < 2 ? (
          <p class="muted" style={{ marginTop: 16 }}>Cargando el gráfico…</p>
        ) : (
          <>
            <Sparkline
              values={candles.map((c) => ({ value: c.usd }))}
              selected={scrub}
              onScrub={setScrub}
            />
            <div class="row" style={{ marginTop: 8, alignItems: "flex-start" }}>
              <div>
                <div class="tiny">{stamp(candles[0]!.at, horizon)}</div>
                <div>{Fmt.usd(candles[0]!.usd)}</div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div class="tiny">{stamp(candles.at(-1)!.at, horizon)}</div>
                <div>{Fmt.usd(candles.at(-1)!.usd)}</div>
              </div>
            </div>
          </>
        )}
      </Card>

      {usd !== null && dollar !== null && (
        <Card>
          <SectionTitle>Conversor</SectionTitle>
          <NumberField
            label="Bitcoin"
            suffix="BTC"
            decimals
            value={btcText}
            onChange={(raw) => {
              setBtcText(raw);
              const parsed = Fmt.parseNumber(raw);
              setClpText(parsed === null ? ""
                : btcToClp(money(String(parsed)), usd, dollar.value).toString());
            }}
          />
          <NumberField
            label={`Pesos (al ${Fmt.dayMonth(dollar.date)})`}
            suffix="CLP"
            decimals={false}
            value={clpText === "" && btcText === "1"
              ? btcToClp(money(1), usd, dollar.value).toString() : clpText}
            onChange={(raw) => {
              setClpText(raw);
              const parsed = Fmt.parseNumber(raw);
              setBtcText(parsed === null ? ""
                : clpToBtc(money(String(parsed)), usd, dollar.value).toString().replace(".", ","));
            }}
          />
          {inUf !== null && <KeyValue label="Un bitcoin en UF" value={Fmt.uf(inUf)} />}
        </Card>
      )}

      <div class="footer">
        <p class="tiny">
          Precio de mercado en dólares, convertido a pesos con el dólar observado
          que publica el Banco Central una vez por día hábil. No es una cotización
          de compra ni de venta.
        </p>
      </div>
    </>
  );
};

/** The chart's endpoint labels: the domain says how precise, this says how it reads. */
const stamp = (at: number, h: Horizon): string => {
  switch (stampKind(h)) {
    case "time": return Fmt.timeHm(at);
    case "day": return Fmt.dayMonthNum(at);
    case "month": return Fmt.monthYearOf(at);
  }
};

const shareText = (
  usd: Money, clp: Money | null, inUf: Money | null,
  dollarDate: string | null, source: string,
): string => [
  "*Bitcoin*",
  Fmt.usd(usd),
  ...(clp === null ? [] : [Fmt.clp(clp)]),
  ...(inUf === null ? [] : [Fmt.uf(inUf)]),
  "",
  ...(source === "" ? [] : [`Fuente: ${source}`]),
  ...(dollarDate === null ? [] : [`Dólar observado del ${Fmt.dayMonth(dollarDate)}`]),
].join("\n");
