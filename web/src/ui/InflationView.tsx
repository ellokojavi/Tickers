import { useMemo, useState } from "preact/hooks";
import * as Fmt from "../core/format.ts";
import { money } from "../domain/money.ts";
import { today as todayIso, type IsoDate } from "../domain/dates.ts";
import { convert } from "../domain/ufReajuste.ts";
import { resolve, SERIES_START } from "../domain/ufLookup.ts";
import type { ReajusteResult } from "../domain/models.ts";
import type { UfData } from "../data/useUfData.ts";
import { Card, DateField, KeyValue, NumberField, SectionTitle } from "./components.tsx";
import { ShareButton } from "./share.tsx";
import { SwapIcon } from "./icons.tsx";

/**
 * Restates an amount between two dates, not two months.
 *
 * The CPI is a monthly statistic, so there is no price level for a given day;
 * the UF is published every calendar day. Asking for dates makes the question
 * well posed and leaves exactly one answer, which is why there is no second,
 * parallel reading beside this one.
 */
export const InflationView = ({ data }: { data: UfData }) => {
  const today = todayIso();
  const [amountText, setAmountText] = useState("4000");
  const [from, setFrom] = useState<IsoDate>("1990-01-01");
  const [to, setTo] = useState<IsoDate>(today);

  const latest = data.lastPublished ?? today;

  const ufOn = (date: IsoDate) => {
    const exact = data.series.find((v) => v.date === date) ?? null;
    const nearest = data.series.filter((v) => v.date <= date).at(-1) ?? null;
    const outcome = resolve(date, exact, nearest, data.lastPublished, today);
    return outcome.kind === "exact" || outcome.kind === "nearest" ? outcome.value : null;
  };

  const result = useMemo((): ReajusteResult | null => {
    const amount = Fmt.parseNumber(amountText);
    if (amount === null || amount <= 0) return null;
    const a = ufOn(from);
    const b = ufOn(to);
    if (a === null || b === null) return null;
    return convert(money(String(amount)), a, b);
  }, [amountText, from, to, data.series]);

  return (
    <>
      <Card>
        <NumberField
          label="Monto en pesos"
          suffix="CLP"
          decimals={false}
          value={amountText}
          onChange={setAmountText}
        />
        <DateField label="Desde" value={from} onChange={setFrom} min={SERIES_START} max={latest} />
        <div class="row" style={{ justifyContent: "center" }}>
          <button
            type="button"
            class="btn icon"
            aria-label="Invertir las fechas"
            onClick={() => { const a = from; setFrom(to); setTo(a); }}
          ><SwapIcon /></button>
        </div>
        <DateField
          label="Hasta"
          value={to}
          onChange={setTo}
          min={SERIES_START}
          max={latest}
          quick={to === today ? undefined : { label: "Hoy", onClick: () => setTo(today) }}
        />
      </Card>

      {result === null ? (
        <Card>
          <p class="muted">
            Sin valor de la UF para esa fecha. La serie va del {Fmt.shortDate(SERIES_START)} al{" "}
            {Fmt.shortDate(latest)}.
          </p>
        </Card>
      ) : (
        <Card>
          <div class="row">
            <span class="muted">
              {Fmt.clp(result.amount)} del {Fmt.longDate(result.from)} equivalen a
            </span>
            <ShareButton
              what="la equivalencia"
              title="Equivalencia de valores"
              text={buildShare(result)}
            />
          </div>
          <p class="display">{Fmt.clp(result.adjustedAmount)}</p>
          <p class="muted">del {Fmt.longDate(result.to)}</p>
          <hr />
          <KeyValue label={`UF el ${Fmt.shortDate(result.from)}`} value={Fmt.clpExact(result.ufAtFrom)} />
          <KeyValue label="Equivale a" value={Fmt.uf4(result.ufUnits)} />
          <KeyValue label={`UF el ${Fmt.shortDate(result.to)}`} value={Fmt.clpExact(result.ufAtTo)} />
          <hr />
          <KeyValue label="Reajuste" value={Fmt.factor(result.factor)} />
          <KeyValue label="Variación acumulada" value={Fmt.pct(result.variationPct)} />
          <KeyValue label="Equivalente anual" value={Fmt.pct(result.annualisedPct)} />
          <KeyValue label="Período" value={Fmt.period(result.days)} />
        </Card>
      )}

      <Card>
        <SectionTitle>Cómo se calcula</SectionTitle>
        <p class="muted">
          El monto se convierte a UF en la fecha inicial y se vuelve a pesos en la fecha final. Es
          el mismo mecanismo con que se reajustan contratos, arriendos y deudas en Chile, y como la
          UF se publica todos los días, el cálculo es exacto al día.
        </p>
        <p class="muted">
          La UF de un día se reajusta con el IPC del mes anterior, así que refleja la inflación con
          ese desfase.
        </p>
      </Card>
    </>
  );
};

/**
 * The result card as a message: the equivalence, the arithmetic behind it and
 * the UF values it rests on, all of which are on the card. The sentence about
 * the method belongs to the card below this one and stays there.
 */
const buildShare = (r: ReajusteResult): string => [
  "*Equivalencia de valores*", "",
  `${Fmt.clp(r.amount)} del ${Fmt.longDate(r.from)}`,
  "equivalen a",
  `${Fmt.clp(r.adjustedAmount)} del ${Fmt.longDate(r.to)}`, "",
  `Reajuste: ${Fmt.factor(r.factor)}`,
  `Variación acumulada: ${Fmt.pct(r.variationPct)}`,
  `Equivalente anual: ${Fmt.pct(r.annualisedPct)}`,
  `Período: ${Fmt.period(r.days)}`, "",
  "*Cálculo por UF*",
  `UF el ${Fmt.shortDate(r.from)}: ${Fmt.clpExact(r.ufAtFrom)}`,
  `Equivale a: ${Fmt.uf4(r.ufUnits)}`,
  `UF el ${Fmt.shortDate(r.to)}: ${Fmt.clpExact(r.ufAtTo)}`,
].join("\n");
