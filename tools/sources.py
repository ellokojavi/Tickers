#!/usr/bin/env python3
"""Where the bundled series come from, and in what order they are tried.

The app reads data along two paths, and they have opposite constraints. This
module serves the first one.

  the generators, here     run in CI      a secret key is fine   CORS irrelevant
  the live refresh         runs in a tab  no key can be secret   CORS required

That difference is the whole reason this file exists. The live refresh is stuck
with a keyless source that sends CORS headers, and there is exactly one:
mindicador.cl, which publishes no terms of use at all. But the generators run in
a GitHub Actions job, where a key is a secret like any other — so they can pull
from the CMF, the financial regulator's own API, whose terms explicitly
authorise republishing its data in a third-party application provided the source
is named with a link to its site. That is the strongest legal footing available
to this project, and the bundled series is where almost every figure the app
shows actually comes from.

So: the CMF when a key is present, mindicador.cl when it is not. Nothing breaks
without a key — the series is byte-identical either way, because both ultimately
publish the Banco Central's figures — and the moment `CMF_API_KEY` exists in the
environment the generators move onto the authorised source without another
change.

**The Banco Central's own API (si3.bcentral.cl) is the more complete source** —
it is the only one carrying the TPM, the Imacec, unemployment and the copper
price — and it is deliberately not used here yet. It sends no CORS header, which
rules it out for the browser, and its credentials could not be obtained to
verify the response shape. See docs/DATA.md.

Whatever answers, the result is validated before it lands: the seed tests read
the shipped files and fail the build on a missing day, an implausible jump or a
series that no longer reproduces the published CPI. A source swap cannot quietly
corrupt the data.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

CMF_BASE = "https://api.cmfchile.cl/api-sbifv3/recursos_api"
MINDICADOR_BASE = "https://mindicador.cl/api"

# Named on every request so an operator reading their logs can tell who this is.
_AGENT = "Tickers seed builder (+https://github.com/ellokojavi/Tickers)"

# Which source answered for how many years. Printed at the end of a run, and
# written into the file's header, so the provenance of a series is never a
# guess.
served = {}


def _get(url, timeout=30):
    request = urllib.request.Request(url, headers={"User-Agent": _AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.load(response)


def _chilean_number(text):
    """The CMF returns numbers written the way Chile writes them: "40.880,36"."""
    return float(str(text).replace(".", "").replace(",", "."))


def _rows_of(payload):
    """The CMF wraps its rows in a key named after the resource - "UFs" for the
    UF, and something else for every other series. Only one value in the object
    is a list, so taking that is more robust than keeping a table of names that
    would be wrong the first time a new resource is added."""
    if not isinstance(payload, dict):
        return []
    for value in payload.values():
        if isinstance(value, list):
            return value
    return []


def _from_cmf(code, year, key):
    payload = _get(f"{CMF_BASE}/{code}/{year}?apikey={key}&formato=json")
    out = []
    for row in _rows_of(payload):
        if not isinstance(row, dict):
            continue
        fecha, valor = row.get("Fecha"), row.get("Valor")
        if not fecha or valor in (None, ""):
            continue
        try:
            out.append({"fecha": str(fecha)[:10], "valor": _chilean_number(valor)})
        except ValueError:
            continue
    return out


def _from_mindicador(code, year):
    payload = _get(f"{MINDICADOR_BASE}/{code}/{year}")
    return [
        {"fecha": str(p["fecha"])[:10], "valor": p["valor"]}
        for p in payload.get("serie", [])
        if isinstance(p, dict)
        and isinstance(p.get("valor"), (int, float))
        and p.get("fecha")
    ]


def _explain(error):
    """The CMF refuses in JSON rather than in a status line alone: an invalid
    key is a 421 carrying {"Mensaje": "API key no valida"}. Saying which is the
    difference between a log someone can act on and one they cannot."""
    if isinstance(error, urllib.error.HTTPError):
        try:
            body = json.loads(error.read().decode("utf-8", "replace"))
            message = body.get("Mensaje") or body.get("mensaje")
            if message:
                return f"HTTP {error.code}: {message}"
        except Exception:
            pass
        return f"HTTP {error.code}"
    return str(error)


def chain_for(code):
    """The sources to try for one series, best first."""
    key = os.environ.get("CMF_API_KEY", "").strip()
    chain = []
    if key:
        chain.append(("CMF", lambda year: _from_cmf(code, year, key)))
    chain.append(("mindicador.cl", lambda year: _from_mindicador(code, year)))
    return chain


def fetch_year(code, year, attempts=3):
    """One year of a daily series, from the first source that answers.

    A source that fails for one year is not abandoned for the rest: an outage
    is usually a minute, not a day, and the year it lost is retried before the
    next source is tried at all. A year nobody can serve returns empty, and the
    caller's own floor - it refuses to write a truncated series - is what turns
    that into a failed build rather than a quietly shorter file.
    """
    for name, fetch in chain_for(code):
        for attempt in range(attempts):
            try:
                rows = fetch(year)
                if rows:
                    served[name] = served.get(name, 0) + 1
                    return rows, name
                break  # An empty answer is an answer; move to the next source.
            except Exception as error:  # noqa: BLE001 - any failure means "try the next"
                if attempt == attempts - 1:
                    print(f"  !! {code} {year} via {name}: {_explain(error)}", file=sys.stderr)
                else:
                    time.sleep(2 * (attempt + 1))
    return [], None


def provenance():
    """One line for the file header, naming who actually answered."""
    if not served:
        return "sin fuente"
    parts = [f"{name} ({years} anos)" for name, years in sorted(served.items())]
    return ", ".join(parts)


def report():
    print("\nfuentes que respondieron:")
    for name, years in sorted(served.items()):
        print(f"  {name}: {years} ano(s)")
    if "CMF" not in served:
        print(
            "  nota: sin CMF_API_KEY en el entorno, asi que no se consulto la\n"
            "        fuente oficial. Ver docs/DATA.md.",
        )
