import { useEffect, useState } from "preact/hooks";
import { parseSeedText } from "./seed.ts";
import type { UfValue } from "../domain/models.ts";

/**
 * The daily dólar observado series, shipped as a static asset beside the UF's.
 *
 * Same file format and same parser, deliberately. The dollar is published on
 * business days only, and "no value today" is exactly what an empty line in
 * that format already means, so weekends and holidays are gaps rather than
 * invented numbers.
 *
 * The values come back as UfValue because that type is a date and an amount and
 * nothing more; the name has outlived its accuracy now that three series use it.
 */
export interface UsdData {
  readonly series: readonly UfValue[];
  readonly loading: boolean;
}

const ASSET = `${import.meta.env.BASE_URL}usd_daily.txt`;

export const useUsdData = (): UsdData => {
  const [series, setSeries] = useState<readonly UfValue[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let live = true;
    void fetch(ASSET)
      .then((r) => r.text())
      .then((text) => { if (live) setSeries(parseSeedText(text)); })
      .catch(() => { /* the screen says so; there is nothing to retry against */ })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, []);

  return { series, loading };
};
