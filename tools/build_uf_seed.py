#!/usr/bin/env python3
"""Build the bundled UF monthly anchor dataset.

The UF is re-adjusted daily so that UF(9th of month M+1) / UF(9th of month M)
equals exactly (1 + IPC variation of month M-1). That makes the UF series a
high-precision proxy for the CPI index: taking the UF value on the 9th of each
month yields a chained price index with ~1e-7 relative precision, far better
than chaining CPI variations published with a single decimal.

Output: app/src/main/assets/uf_monthly_seed.json
"""
import json, sys, time, urllib.request
from datetime import date

START_YEAR = 1977
END_YEAR = date.today().year
OUT = "app/src/main/assets/uf_monthly_seed.json"


def fetch(year):
    url = f"https://mindicador.cl/api/uf/{year}"
    for attempt in range(4):
        try:
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
        daily[item["fecha"][:10]] = item["valor"]
    print(f"  {y}: {len(serie):>3} days")
    time.sleep(0.25)

# Anchor = UF value on the 9th of each month (fall back to nearest earlier day).
anchors = {}
for ym_year in range(START_YEAR, END_YEAR + 2):
    for m in range(1, 13):
        for day in (9, 8, 7, 10, 11):
            key = f"{ym_year:04d}-{m:02d}-{day:02d}"
            if key in daily:
                anchors[f"{ym_year:04d}-{m:02d}"] = daily[key]
                break

anchors = dict(sorted(anchors.items()))
payload = {
    "source": "Banco Central de Chile / CMF, via mindicador.cl",
    "description": "UF value on the 9th of each month; used as a chained price index anchor.",
    "generated": date.today().isoformat(),
    "anchors": anchors,
}
with open(OUT, "w") as f:
    json.dump(payload, f, separators=(",", ":"))

ks = list(anchors)
print(f"\nanchors: {len(anchors)}  range: {ks[0]} .. {ks[-1]}")
print(f"written: {OUT}")
