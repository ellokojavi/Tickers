import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import * as Fmt from "../core/format.ts";
import { money, type Money } from "../domain/money.ts";
import { btcConvert } from "../domain/converter.ts";
import { chartSummary, currentOf } from "../domain/ufEngine.ts";
import { today as todayIso } from "../domain/dates.ts";
import {
  HORIZONS, btcUsdToClp, btcUsdToUf, chartPlan,
  fetchErrorCopy, horizonLabel, stampKind, type Horizon,
} from "../domain/btc.ts";
import { fetchCandles, fetchSpot, type Candle, type Spot, type SpotFailure } from "../data/btcApi.ts";
import type { UfData } from "../data/useUfData.ts";
import { Card, KeyValue, NumberField, Pill, SectionTitle } from "./components.tsx";
import { HistoryCard } from "./HistoryCard.tsx";
import { ShareButton } from "./share.tsx";

/** How long a spot price is shown as current before it is called stale. */
const FRESH_MS = 60_000;

const DAY_MS = 24 * 60 * 60 * 1000;

const RANGES = HORIZONS.map((h) => ({ key: h, label: horizonLabel[h] }));

const isOk = (r: Spot | SpotFailure | null): r is Spot => r !== null && "usd" in r;

export const BtcView = ({ data }: { data: UfData }) => {
  const [spot, setSpot] = useState<Spot | SpotFailure | null>(null);
  const [horizon, setHorizon] = useState<Horizon>("D30");
  const [candles, setCandles] = useState<readonly Candle[]>([]);
  const [chartLoading, setChartLoading] = useState(true);
  const [scrub, setScrub] = useState<number | null>(null);
  const [btcText, setBtcText] = useState("1");
  const [usdText, setUsdText] = useState("");
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
    setChartLoading(true);
    setScrub(null);
    void fetchCandles(horizon)
      .then((c) => { if (wanted.current === horizon) setCandles(c); })
      .catch(() => { if (wanted.current === horizon) setCandles([]); })
      .finally(() => { if (wanted.current === horizon) setChartLoading(false); });
    const id = window.setInterval(() => {
      void fetchCandles(horizon)
        .then((c) => { if (wanted.current === horizon) setCandles(c); })
        .catch(() => {});
    }, chartPlan(horizon).refreshMs);
    return () => window.clearInterval(id);
  }, [horizon]);

  /**
   * One value is typed, the other two follow. The arithmetic lives in the
   * domain because it has to give the same answers here and on Android, and
   * because deriving one rounded figure from another is how a converter starts
   * disagreeing with itself.
   */
  const setConverter = (field: "btc" | "usd" | "clp", raw: string, price: Money, rate: Money) => {
    if (field === "btc") setBtcText(raw);
    if (field === "usd") setUsdText(raw);
    if (field === "clp") setClpText(raw);

    const parsed = Fmt.parseNumber(raw);
    const result = parsed === null ? null
      : btcConvert(field, money(String(parsed)), price, rate);
    if (result === null) {
      if (field !== "btc") setBtcText("");
      if (field !== "usd") setUsdText("");
      if (field !== "clp") setClpText("");
      return;
    }
    if (field !== "btc") setBtcText(result.btc.toString().replace(".", ","));
    if (field !== "usd") setUsdText(result.usd.toString().replace(".", ","));
    if (field !== "clp") setClpText(result.clp.toString());
  };

  // The card opens on one bitcoin rather than empty, so it answers before use.
  const opening = (price: Money, rate: Money) =>
    btcText === "1" ? btcConvert("btc", money(1), price, rate) : null;
  const usdShown = (price: Money, rate: Money): string =>
    usdText === "" ? opening(price, rate)?.usd.toString().replace(".", ",") ?? "" : usdText;
  const clpShown = (price: Money, rate: Money): string =>
    clpText === "" ? opening(price, rate)?.clp.toString() ?? "" : clpText;

  const usd = isOk(spot) ? spot.usd : null;

  // The chart draws `value`; a candle's is its dollar price.
  const points = useMemo(() => candles.map((c) => ({ ...c, value: c.usd })), [candles]);

  // Candles are stamped in millis, so the days are whole ones.
  const summary = candles.length >= 2
    ? chartSummary(
        candles[0]!.usd, candles.at(-1)!.usd,
        Math.floor((candles.at(-1)!.at - candles[0]!.at) / DAY_MS),
      )
    : null;

  const clp = usd !== null && dollar !== null ? btcUsdToClp(usd, dollar.value) : null;
  const inUf = usd !== null && dollar !== null && uf !== null
    ? btcUsdToUf(usd, dollar.value, uf) : null;

  const stale = isOk(spot) && now - spot.at > FRESH_MS;

  return (
    <>
      <Card class="btc-hero">
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
            <p class="hero-value">{usd === null ? "—" : Fmt.usd(usd)}</p>
            {clp !== null && (
              <p class="display" style={{ fontSize: 24, margin: "2px 0 0" }}>{Fmt.clp(clp)}</p>
            )}
            {inUf !== null && (
              <div class="row" style={{ justifyContent: "flex-start", gap: 14, marginTop: 8 }}>
                <span class="muted">{Fmt.uf(inUf)}</span>
              </div>
            )}
            <div class="row" style={{ justifyContent: "flex-start", gap: 8, marginTop: 14 }}>
              {isOk(spot) && <Pill>{spot.source}</Pill>}
              {dollar !== null && (
                <Pill>Dólar del {Fmt.dayMonth(dollar.date)}</Pill>
              )}
            </div>
          </>
        )}
      </Card>

      <HistoryCard
        class="btc-chart"
        ranges={RANGES}
        range={horizon}
        onRange={setHorizon}
        points={points}
        stamp={(c) => stamp(c.at, horizon)}
        amount={(c) => Fmt.usd(c.usd)}
        summary={summary}
        scrub={scrub}
        onScrub={setScrub}
        loading={chartLoading}
        chartLabel="Evolución del precio del bitcoin"
      />

      {usd !== null && dollar !== null && (
        <Card class="btc-conv">
          <SectionTitle>Conversor</SectionTitle>
          <NumberField
            label="Bitcoin"
            suffix="BTC"
            value={btcText}
            onChange={(raw) => setConverter("btc", raw, usd, dollar.value)}
          />
          <NumberField
            label="Dólares"
            suffix="USD"
            value={usdShown(usd, dollar.value)}
            onChange={(raw) => setConverter("usd", raw, usd, dollar.value)}
            action={(
              <span class="field-action">
                <ShareButton
                  what="el precio en dólares"
                  title="Precio del bitcoin"
                  text={convShare(btcText, usdShown(usd, dollar.value), null)}
                />
              </span>
            )}
          />
          <NumberField
            label="Pesos"
            suffix="CLP"
            decimals={false}
            // The dollars above are the market's own price; only the pesos pass
            // through a rate with a publication day, so only they carry it.
            support={`Dólar observado del ${Fmt.dayMonth(dollar.date)}`}
            value={clpShown(usd, dollar.value)}
            onChange={(raw) => setConverter("clp", raw, usd, dollar.value)}
            action={(
              <span class="field-action">
                <ShareButton
                  what="el precio en dólares y pesos"
                  title="Precio del bitcoin"
                  text={convShare(btcText, usdShown(usd, dollar.value), clpShown(usd, dollar.value))}
                />
              </span>
            )}
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

/**
 * What the share buttons send. Deliberately one line: it is meant to be pasted
 * into a conversation, not read as a report.
 */
const convShare = (btcText: string, usd: string, clp: string | null): string => {
  const amount = Fmt.parseNumber(btcText);
  const head = amount === 1 ? "Bitcoin de hoy" : `${btcText} BTC`;
  return [
    head,
    Fmt.usd(money(usd === "" ? "0" : usd.replace(",", "."))),
    ...(clp === null || clp === "" ? [] : [`CLP${Fmt.clp(money(clp))}`]),
  ].join(", ");
};
