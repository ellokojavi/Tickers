#!/usr/bin/env python3
"""Build the bundled daily dólar observado series.

Same format and same parser as the UF series, deliberately. The dollar is
published on business days only, so roughly two days in seven have no value —
and "no value" is exactly what an empty line already means in that format. The
alternative, storing dates alongside values, would have needed a second parser
for no gain: empty lines cost almost nothing once compressed.

Gaps are never filled. A Saturday has no dólar observado, and inventing one
would be inventing a rate people might quote. Screens carry the last published
value forward and say which day it is from.

Output: app/src/main/assets/usd_daily.txt
"""
import json, sys, time, urllib.request
from datetime import date, timedelta

OUT = "app/src/main/assets/usd_daily.txt"
START_YEAR, END_YEAR = 1984, date.today().year

# The dollar is a floating rate and moves far more than the UF, which is an
# index. This is loose enough to leave every real move alone and tight enough to
# catch the failure that actually happens: a value off by a factor of ten.
MAX_DAILY_CHANGE = 0.15


def fetch(year):
    for attempt in range(4):
        try:
            url = f"https://mindicador.cl/api/dolar/{year}"
            with urllib.request.urlopen(url, timeout=30) as r:
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
        value = item.get("valor")
        if isinstance(value, (int, float)) and value > 0:
            daily[item["fecha"][:10]] = value
    print(f"  {y}: {len(serie):>3}")

if not daily:
    sys.exit("no data fetched")

# Plausibility.
#
# Two naive rules both fail here, and it is worth saying why.
#
# "Reject anything that moves more than X per day" deletes history: the peso was
# devalued 23,5% on 1984-09-21 and stayed there, and that is a real event.
#
# "Reject anything far from both neighbours" fails the other way: the UF series'
# real corruption was two consecutive bad days, and each of those looked
# perfectly consistent with the other.
#
# What actually separates them is persistence. A devaluation is a step and the
# new level holds; corruption is an excursion and the series comes back. So a
# run of values far from the last accepted level is held in suspense: if the
# series returns to that level within a few published days, the whole run is
# dropped; if it does not, the run is a step and is kept.
MAX_DAILY_CHANGE = 0.15

# How close a return has to be to count as "back where it was".
RETURN_TOLERANCE = 0.05

# A run longer than this has stopped being an excursion and is the new normal.
MAX_EXCURSION = 5


def per_day(a, b, days):
    return abs(b / a - 1) / max(1, days)


ordered = sorted(daily)
kept, rejected = {}, []
baseline = None          # last accepted value and its date
excursion = []           # values in suspense: far from baseline, not yet judged


def settle(as_step):
    """Resolve the suspended run, either into the series or into the bin."""
    global baseline
    for key in excursion:
        if as_step:
            kept[key] = daily[key]
            baseline = (date.fromisoformat(key), daily[key])
        else:
            rejected.append((key, daily[key], baseline[1] if baseline else None))
    excursion.clear()


for key in ordered:
    value = daily[key]
    today = date.fromisoformat(key)

    if baseline is None:
        kept[key] = value
        baseline = (today, value)
        continue

    far = per_day(baseline[1], value, (today - baseline[0]).days) > MAX_DAILY_CHANGE

    if excursion:
        # Already in suspense: has the series come back to where it was?
        if abs(value / baseline[1] - 1) <= RETURN_TOLERANCE:
            settle(as_step=False)
            kept[key] = value
            baseline = (today, value)
            continue
        excursion.append(key)
        if len(excursion) > MAX_EXCURSION:
            settle(as_step=True)
        continue

    if far:
        excursion.append(key)
        continue

    kept[key] = value
    baseline = (today, value)

# Anything still in suspense at the end never came back, so it was a step.
settle(as_step=True)

if rejected:
    print("\nvalores descartados (excursion que volvio, no escalon):")
    for key, value, prev in rejected:
        print(f"  {key}: {value} (nivel establecido {prev})")

keys = sorted(kept)
start, end = date.fromisoformat(keys[0]), date.fromisoformat(keys[-1])

lines = [
    "# Tickers - serie diaria del dolar observado",
    "# fuente: Banco Central de Chile, via mindicador.cl",
    f"# generado: {date.today().isoformat()}",
    f"# inicio: {start.isoformat()}",
    f"# fin: {end.isoformat()}",
    "# unidad: centavos de peso (valor x 100); linea vacia = dia sin publicacion",
]

missing = 0
d = start
while d <= end:
    v = kept.get(d.isoformat())
    if v is None:
        lines.append("")
        missing += 1
    else:
        lines.append(str(round(v * 100)))
    d += timedelta(days=1)

with open(OUT, "w") as f:
    f.write("\n".join(lines) + "\n")

total = (end - start).days + 1
print(f"\n{OUT}: {total} dias, {total - missing} con valor, {missing} sin publicacion")
print(f"periodo {start} .. {end}")
