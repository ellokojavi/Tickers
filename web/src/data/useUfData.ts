import { useEffect, useState } from "preact/hooks";
import { parseSeedText } from "./seed.ts";
import { fetchIndicators, fetchUfYear } from "./api.ts";
import { filterImplausible } from "../domain/ufSanity.ts";
import { today, year as yearOf, type IsoDate } from "../domain/dates.ts";
import type { DataSourceKey, Indicator, DatedValue } from "../domain/models.ts";

export interface UfData {
  readonly series: readonly DatedValue[];
  readonly indicators: readonly Indicator[];
  readonly source: DataSourceKey;
  readonly loading: boolean;
  readonly stale: boolean;
  readonly lastPublished: IsoDate | null;
  refresh: () => void;
}

/**
 * The whole series arrives with the page as a static file, so every screen
 * works before any network call and keeps working without one. The refresh
 * only ever asks for the current year, and anything it returns is checked for
 * plausibility before it is allowed to override the bundled data.
 */
export const useUfData = (): UfData => {
  const [series, setSeries] = useState<readonly DatedValue[]>([]);
  const [indicators, setIndicators] = useState<readonly Indicator[]>([]);
  const [source, setSource] = useState<DataSourceKey>("BUNDLED");
  const [loading, setLoading] = useState(true);
  const [stale, setStale] = useState(false);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    const run = async () => {
      let base: readonly DatedValue[] = series;
      if (base.length === 0) {
        try {
          const response = await fetch(`${import.meta.env.BASE_URL}uf_daily.txt`, {
            signal: controller.signal,
          });
          base = parseSeedText(await response.text());
          if (!cancelled) {
            setSeries(base);
            setLoading(false);
          }
        } catch {
          if (!cancelled) setLoading(false);
          return;
        }
      }

      try {
        const current = yearOf(today());
        const fetched = await fetchUfYear(current, controller.signal);
        const anchor = base.filter((v) => yearOf(v.date) < current).at(-1) ?? null;
        const clean = filterImplausible(fetched, anchor);

        if (!cancelled && clean.length > 0) {
          const merged = new Map(base.map((v) => [v.date, v]));
          for (const v of clean) merged.set(v.date, v);
          setSeries([...merged.values()].sort((a, b) => (a.date < b.date ? -1 : 1)));
          setSource("MINDICADOR");
          setStale(false);
        }
        const other = await fetchIndicators(controller.signal);
        if (!cancelled) setIndicators(other);
      } catch {
        // Offline, or the source is down. The bundled series still answers
        // everything; the screen just says how fresh it is.
        if (!cancelled) setStale(true);
      }
    };

    void run();
    return () => {
      cancelled = true;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick]);

  const lastPublished = series.length === 0 ? null : series[series.length - 1]!.date;

  return {
    series,
    indicators,
    source,
    loading,
    stale,
    lastPublished,
    refresh: () => setTick((t) => t + 1),
  };
};
