import { money } from "../domain/money.ts";
import { parseIso, type IsoDate } from "../domain/dates.ts";
import type { Indicator, DatedValue } from "../domain/models.ts";

/**
 * mindicador.cl, which mirrors Banco Central data and sends
 * Access-Control-Allow-Origin, so a static page can read it directly with no
 * server of its own.
 *
 * The CMF is the official source but its API needs a key, which a public web
 * page cannot hold secret. The bundled series is regenerated daily from the
 * source of record instead, so the page is never more than a day behind even
 * if this endpoint is unreachable.
 */
const BASE = "https://mindicador.cl/api";

interface Point { fecha?: unknown; valor?: unknown }

const toDatedValue = (p: Point): DatedValue | null => {
  if (typeof p.fecha !== "string" || typeof p.valor !== "number") return null;
  const date = parseIso(p.fecha.slice(0, 10));
  if (date === null || !Number.isFinite(p.valor)) return null;
  // Through the string form so a two-decimal value keeps its exact cents.
  return { date, value: money(String(p.valor)) };
};

export const fetchUfYear = async (year: number, signal?: AbortSignal): Promise<DatedValue[]> => {
  const response = await fetch(`${BASE}/uf/${year}`, { signal });
  if (!response.ok) throw new Error(`mindicador respondió ${response.status}`);
  const body = (await response.json()) as { serie?: Point[] };
  return (body.serie ?? [])
    .map(toDatedValue)
    .filter((v): v is DatedValue => v !== null)
    .sort((a, b) => (a.date < b.date ? -1 : 1));
};

interface IndicatorPayload {
  codigo?: unknown; nombre?: unknown; unidad_medida?: unknown;
  fecha?: unknown; valor?: unknown;
}

const toIndicator = (p: IndicatorPayload | undefined): Indicator | null => {
  if (p === undefined) return null;
  if (typeof p.codigo !== "string" || typeof p.valor !== "number") return null;
  const date: IsoDate | null =
    typeof p.fecha === "string" ? parseIso(p.fecha.slice(0, 10)) : null;
  if (date === null) return null;
  return {
    code: p.codigo,
    name: typeof p.nombre === "string" ? p.nombre : p.codigo,
    unit: typeof p.unidad_medida === "string" ? p.unidad_medida : "",
    date,
    value: money(String(p.valor)),
  };
};

export const fetchIndicators = async (signal?: AbortSignal): Promise<Indicator[]> => {
  const response = await fetch(BASE, { signal });
  if (!response.ok) throw new Error(`mindicador respondió ${response.status}`);
  const body = (await response.json()) as Record<string, IndicatorPayload | undefined>;
  return (["ivp", "dolar", "euro", "utm", "ipc"] as const)
    .map((key) => toIndicator(body[key]))
    .filter((v): v is Indicator => v !== null);
};
