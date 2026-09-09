#!/usr/bin/env python3
"""Build the bundled full daily UF series.

The whole series is ~18.000 days and compresses to a few tens of kilobytes, so
shipping it inside the APK is cheaper than fetching years on demand: charts
work offline from first launch and the app only ever has to ask for the current
year afterwards.

Format is deliberately not JSON. Days are contiguous, so only the first date is
stored and each following line is one day's value in centavos. An empty line is
a day the source has no value for — never interpolated. Parsing is a readLines
plus toInt, with no tokeniser, which matters when it runs at startup.

Output: app/src/main/assets/uf_daily.txt
"""
import json, sys, time, urllib.request
from datetime import date, timedelta

OUT = "app/src/main/assets/uf_daily.txt"
START_YEAR, END_YEAR = 1977, date.today().year


def fetch(year):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(f"https://mindicador.cl/api/uf/{year}", timeout=30) as r:
                return json.load(r).get("serie", [])
        except Exception as e:
            if attempt == 3:
                print(f"  !! {year} failed: {e}", file=sys.stderr)
                return []
            time.sleep(2 * (attempt + 1))


daily = {}
for y in range(START_YEAR, END_YEAR + 1):
    serie = fetch(y)
    for item in serie:
        daily[item["fecha"][:10]] = item["valor"]
    print(f"  {y}: {len(serie):>3}")
    time.sleep(0.15)

if len(daily) < 17000:
    sys.exit(f"refusing to write a truncated series ({len(daily)} days)")

# The source is not clean: at the time of writing it returns 608.15 for
# 2014-12-29 and 607.38 for 2014-12-30, where the real UF was about 24.627.
# The largest legitimate day-over-day change across the whole 49-year series is
# 0.2633%, so anything past 1% per elapsed day is corruption. Such days are
# dropped and shipped as gaps; the app shows the nearest earlier day and says
# so, which is honest, whereas interpolating would invent an official figure.
MAX_DAILY_CHANGE = 0.01

rejected = []
clean = {}
previous = None
for key in sorted(daily):
    value = daily[key]
    day = date.fromisoformat(key)
    if value <= 0:
        rejected.append((key, value, "no positivo"))
        continue
    if previous is not None:
        gap = max(1, (day - previous[0]).days)
        change = abs(value / previous[1] - 1)
        if change > MAX_DAILY_CHANGE * gap:
            rejected.append((key, value, f"salto de {change * 100:.2f}% en {gap} dia(s)"))
            continue
    clean[key] = value
    previous = (day, value)

if rejected:
    print("\nvalores descartados por implausibles:")
    for key, value, why in rejected:
        print(f"  {key}: {value} ({why})")
if len(rejected) > 20:
    sys.exit("demasiados valores descartados; revisar la fuente antes de publicar")

daily = clean

# --- reconstruccion de dias faltantes -------------------------------------
#
# Dentro de un periodo de reajuste (del 10 de un mes al 9 del siguiente) la UF
# crece a factor diario constante por construccion, asi que un dia perdido se
# reconstruye exactamente desde sus vecinos: no es interpolacion a ojo, es
# recuperar un termino de una progresion geometrica conocida. Solo se hace
# cuando el hueco no cruza un cambio de periodo, donde el factor cambia.


def period_start(d):
    return date(d.year, d.month, 10) if d.day >= 10 else (
        date(d.year - 1, 12, 10) if d.month == 1 else date(d.year, d.month - 1, 10))


known = sorted(date.fromisoformat(k) for k in daily)
repaired = []
first, last = known[0], known[-1]
index = {d: i for i, d in enumerate(known)}

cursor = first
while cursor <= last:
    if cursor.isoformat() not in daily:
        before = max((d for d in known if d < cursor), default=None)
        after = min((d for d in known if d > cursor), default=None)
        if before and after and period_start(before) == period_start(cursor) == period_start(after):
            span = (after - before).days
            step = (cursor - before).days
            value = daily[before.isoformat()] * (
                daily[after.isoformat()] / daily[before.isoformat()]
            ) ** (step / span)
            daily[cursor.isoformat()] = round(value, 2)
            repaired.append((cursor.isoformat(), round(value, 2)))
    cursor += timedelta(days=1)

if repaired:
    print("\ndias reconstruidos desde el periodo de reajuste:")
    for key, value in repaired:
        print(f"  {key}: {value}")

keys = sorted(daily)
start = date.fromisoformat(keys[0])
end = date.fromisoformat(keys[-1])

lines = [
    "# Tickers - serie diaria completa de la Unidad de Fomento",
    "# fuente: Banco Central de Chile / CMF, via mindicador.cl",
    f"# generado: {date.today().isoformat()}",
    f"# inicio: {start.isoformat()}",
    f"# fin: {end.isoformat()}",
    "# unidad: centavos de peso (valor x 100); linea vacia = dia sin dato",
]

missing = 0
d = start
while d <= end:
    v = daily.get(d.isoformat())
    if v is None:
        lines.append("")
        missing += 1
    else:
        lines.append(str(int(round(v * 100))))
    d += timedelta(days=1)

with open(OUT, "w") as f:
    f.write("\n".join(lines) + "\n")

total = (end - start).days + 1
print(f"\ndias: {total}  con dato: {total - missing}  sin dato: {missing}")
print(f"rango: {start} .. {end}")
print(f"escrito: {OUT}")
