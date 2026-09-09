import * as Fmt from "../core/format.ts";
import type { ChartSummary } from "../domain/ufEngine.ts";
import type { Money } from "../domain/money.ts";
import { Card, Chips, SectionTitle, Sparkline } from "./components.tsx";

/**
 * The one "Histórico" card. Every series in the app (UF, dollar, bitcoin) is
 * drawn through this so the charts behave the same way without anyone having
 * to remember to make them:
 *
 *  - a row of range chips, then one line of text, then the line, then both
 *    ends labelled with their moment and value;
 *  - the text line carries the window's change and its annualised rate, or
 *    the point under the pointer while scrubbing, or why there is no chart;
 *  - scrubbing changes nothing outside this card: the hero above it keeps
 *    showing today's value, because that is what the screen is for.
 *
 * The view decides what a point's moment and amount look like (`stamp`,
 * `amount`) and where the data comes from; this decides everything else. The
 * Android twin is ui/components/HistoryChartCard.kt.
 */
export const HistoryCard = <R extends string, P extends { readonly value: Money }>(
  { ranges, range, onRange, points, stamp, amount, summary, scrub, onScrub,
    loading = false, message = null, chartLabel, class: extra }: {
    ranges: readonly { key: R; label: string }[];
    range: R;
    onRange: (key: R) => void;
    points: readonly P[];
    stamp: (p: P) => string;
    amount: (p: P) => string;
    summary: ChartSummary | null;
    scrub: number | null;
    onScrub: (index: number | null) => void;
    loading?: boolean;
    message?: string | null;
    /** What the chart is of, for screen readers. */
    chartLabel: string;
    class?: string;
  },
) => {
  const scrubbed = scrub === null ? null : points[scrub] ?? null;
  const first = points[0] ?? null;
  const last = points.length >= 2 ? points[points.length - 1]! : null;

  return (
    <Card class={extra}>
      <SectionTitle>Histórico</SectionTitle>
      <Chips
        label="Rango"
        options={ranges}
        selected={range}
        // A new window starts unscrubbed: an index into the old series means
        // nothing in the new one.
        onSelect={(key) => { onRange(key); onScrub(null); }}
      />
      {message !== null && <p class="tiny" style={{ color: "var(--error)", marginTop: 8 }}>{message}</p>}
      {/* One line above the chart carries either the period summary or, while
          the pointer is down, the point being explored. Below the chart it
          would fall past the fold. minHeight keeps the chart from jumping. */}
      <p class="muted" style={{ minHeight: 24, marginTop: 12 }}>
        {scrubbed !== null
          ? `${stamp(scrubbed)}   ${amount(scrubbed)}`
          : points.length < 2
            ? (loading ? "Cargando el gráfico…" : "Sin datos para este rango")
            : summary !== null
              ? <>
                  <span class={toneOf(summary.periodPct)}>
                    {Fmt.pctSigned(summary.periodPct)} en el período
                  </span>
                  {summary.annualisedPct !== null && <>
                    {" · "}
                    <span class={toneOf(summary.annualisedPct)}>
                      {Fmt.pctSigned(summary.annualisedPct)} anualizado
                    </span>
                  </>}
                </>
              : " "}
      </p>
      <Sparkline values={points} height={CHART_HEIGHT} selected={scrub} onScrub={onScrub} label={chartLabel} />
      {/* Both ends are labelled with their value, so the chart can be read
          without touching it. */}
      {first !== null && last !== null && (
        <div class="row" style={{ marginTop: 8, alignItems: "flex-start" }}>
          <div><div class="tiny">{stamp(first)}</div><div>{amount(first)}</div></div>
          <div style={{ textAlign: "right" }}>
            <div class="tiny">{stamp(last)}</div><div>{amount(last)}</div>
          </div>
        </div>
      )}
    </Card>
  );
};

/** The one chart height. Android draws its charts at the same 200 dp. */
const CHART_HEIGHT = 200;

/** Green up, red down, neutral at zero: each figure by its own sign. */
const toneOf = (v: Money): string => (v.cmp(0) > 0 ? "positive" : v.cmp(0) < 0 ? "negative" : "");
