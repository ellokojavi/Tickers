# Data

Where every number in Tickers comes from, what the sources allow, how the data
is validated and repaired, and how it is kept fresh. The short version: the
complete UF and dollar series ship with the page and are regenerated every
morning; the network only ever refreshes the day's value; nothing is stored
without a plausibility check; and nothing is ever invented.

## Sources

| Series | Source | Notes |
|---|---|---|
| UF, live | [mindicador.cl](https://mindicador.cl) | Third-party mirror of Banco Central data. Sends CORS headers, which is what lets a static page read it |
| UF, bundled | `web/public/uf_daily.txt` | The complete daily series, 1 August 1977 to the day of the last refresh: about 18.000 days, some 50 kB compressed |
| Dólar observado, bundled | `web/public/usd_daily.txt` | Every business day since 2 January 1984, about 10.600 published values. Weekends and holidays are blank lines |
| Companion indicators | mindicador.cl | UTM, TPM, IPC, euro, copper, IVP, Imacec and unemployment, fetched with the day's UF. The dólar observado is fetched with them, and shown as its own card rather than in the list |
| Bitcoin, spot | Coinbase, CoinGecko, Kraken | Tried in order; the first answer wins |
| Bitcoin, candles | Coinbase Exchange | One request per horizon, at the candle width the horizon calls for |

The app does not depend on the network being up. It opens on the bundled
series, which is regenerated daily, so a first load is never more than a day
behind even when every endpoint is unreachable.

The one exception is bitcoin, and it is exactly that: a market price cannot be
bundled. Since the overview is the screen the app opens on, and it shows
bitcoin, opening the app now asks for a spot price and a thirty-day series
where it previously asked for nothing until you went looking. The UF and the
dollar on that screen are still read off the device, so the card is the only
thing waiting, and it says so in words rather than sitting empty.

Two of the keys the source publishes are never shown. `dolar_intercambio` has
not moved since 13 November 2014, so listing it would present a dead figure as
a current one. `bitcoin` is stale here and the app has a live price of its own.
Both exclusions are pinned by a test, as is the order of the rest.

**These arrive on different clocks.** The UTM is monthly, the TPM and the euro
are daily, the Imacec and unemployment run about two months behind, and the
source's IPC is further behind than that — it was serving December 2025 in
September 2026. Every row therefore carries its own date, dated to the month
where the figure means a month. Undated they all read as today's, which is the
kind of wrong a user cannot detect.

Every screen names its source. The UF card carries a badge saying whether the
value came from mindicador.cl, from the bundled data or from the local cache,
and whether that source is official. The bitcoin card names the exchange that
answered and the date of the dollar used to convert it.

### The two paths, and why they take different sources

Data reaches the app two ways, and they have opposite constraints. Confusing
them is what made a single unauthorised source look inevitable.

| | The generators, `tools/` | The live refresh |
|---|---|---|
| Runs in | a GitHub Actions job | the reader's browser |
| A secret key? | **yes**, a repository secret | no — it would be public |
| CORS needed? | no | **yes** |
| Feeds | the complete series: almost every figure shown | only the day's value |

The generators can therefore use a source the browser cannot. They do:
`tools/sources.py` tries the **CMF** first when `CMF_API_KEY` is set in the
environment, and falls back to mindicador.cl when it is not. Without the secret
nothing changes and nothing breaks — the series is byte-identical either way,
since both ultimately publish the Banco Central's figures — and setting it moves
the bundled data onto the source whose terms actually authorise this.

The live refresh has no such option and stays on mindicador.cl.

**Do not put the CMF key in the page.** It is not a security risk — the data is
public — but the quota is 10.000 requests a month and a key visible in a bundle
can be spent by anyone.

### What each source actually offers

Measured against the live endpoints in September 2026, with an `Origin` header
set, rather than taken from documentation:

| Source | CORS | Key | Verdict |
|---|---|---|---|
| [CMF](https://api.cmfchile.cl) | `*` | free, required | **Official.** UF, UTM, dólar, euro, IPC, and the interest-rate series. Its terms explicitly authorise republishing. Used by the generators when the secret is set |
| [Banco Central, API BDE](https://si3.bcentral.cl/Siete/es/Siete/API) | **none** | free, required | The most complete source and the only one carrying the TPM, the Imacec, unemployment and copper. Unusable from a browser; usable from CI. **Not yet wired up** — see below |
| [mindicador.cl](https://mindicador.cl) | `*` | none | Mirrors Banco Central data. Publishes **no terms of use at all**. The only keyless CORS source covering the whole set, so the live refresh has no alternative |
| [datos.gob.cl](https://datos.gob.cl) (CKAN) | `*` | none | Correct licence (ODC-BY) and an official publisher, but the Cochilco copper dataset is **empty and untouched since 2020** |
| api.gael.cloud | `*` | none | **Rejected.** Served a UTM of 69.265 against a real 71.721 |
| cl.dolarapi.com | `*` | none | **Rejected.** Publishes a *market* dollar, not the dólar observado |
| open.er-api.com | `*` | none | **Rejected.** A market euro cross, not the Banco Central's published euro |
| exchangerate.host | `*` | now required | No longer keyless |
| stooq, Yahoo Finance | none | none | No CORS; unusable from the browser |

**The rule those rejections follow is worth stating on its own: a backup has to
publish the same figure, not a similar one.** dolarapi and open.er-api work
perfectly and would quietly replace an official published rate with a market
average. A number that is plausible and different is worse than no number,
which is the same principle as never inventing a value for a day that has none.

### Primary and backup, per indicator

| Indicator | Primary | Backup | Last resort |
|---|---|---|---|
| UF | CMF | Banco Central | mindicador.cl |
| Dólar observado | CMF | Banco Central | mindicador.cl |
| UTM | CMF | Banco Central | mindicador.cl |
| Euro | CMF | Banco Central | mindicador.cl |
| IPC | CMF | Banco Central / INE | mindicador.cl |
| IVP | CMF | — | mindicador.cl |
| TPM | Banco Central | — | mindicador.cl |
| Imacec | Banco Central | — | mindicador.cl |
| Unemployment | Banco Central / INE | — | mindicador.cl |
| Copper | Banco Central | — | mindicador.cl |
| Bitcoin | Coinbase | CoinGecko, Kraken | — |

The first six have a genuine official fallback. The four after them exist
**only** in the Banco Central's API: the CMF does not publish them and no
official source with CORS was found. For those, mindicador.cl is currently the
only route to the browser.

### What is left undone, and what it would take

- **The Banco Central's API is not wired up.** It is the better source and the
  only one with the whole set. It needs free credentials from
  [its API page](https://si3.bcentral.cl/Siete/es/Siete/API), and its response
  shape could not be verified without them, so nothing was written against a
  guess. Its documentation page currently 404s.
- **The CMF path has not run against a real key.** Its request shape and its
  number format are taken from the Android app's own client, which ran against
  the live API for a year, and its captured payload is pinned in
  `tools/test_sources.py`. The refusal path was exercised with an invalid key.
  The first real run will be the first proof.
- **The CMF's array key is guessed for everything except the UF.** It names the
  array after the resource — `UFs` — and only that one was ever verified, so
  the parser takes whichever value in the object is a list rather than keeping
  a table of plurals.
- **Copper, TPM, Imacec and unemployment have no official fallback**, and the
  source's IPC ran nine months behind in September 2026. Every companion figure
  carries its own date on screen for exactly this reason.

### Terms, and what they require

The figures ultimately originate with the Banco Central de Chile and the CMF,
and the CMF's [terms of use](https://api.cmfchile.cl/terminos-de-uso.html)
authorise republishing its data in a third-party application on one condition:
the source must be named, **with a link to its site**, wherever the data is
republished. The "Acerca de" screen names the CMF and links to cmfchile.cl.
That is a requirement, not a courtesy.

mindicador.cl publishes **no terms of use at all**: no licence, no stated
permission to redistribute, no rate limits. It mirrors Banco Central data and
is credited in the same screen, but its legal position is undefined.

**It is the only source the live refresh can use**, and that is worth stating
plainly. A key cannot be kept secret inside a public page, so the CMF path can
never exist in the browser; when the Android channel was removed in September
2026 it went with it, and for a while the bundled series was generated from
mindicador.cl as well. That second half is now closed: the generators run in
CI, where a key is a secret like any other, and they pull from the CMF whenever
`CMF_API_KEY` is set. Almost every figure the app shows comes from the bundled
series, so with the secret in place the unauthorised source is reduced to
refreshing a single day's value.

The bitcoin sources are public price endpoints used within their published
limits, at most one request every thirty seconds for the spot price and one per
horizon change for the chart, and each is named on screen when it answers.

## The bundled series

Both series use the same deliberately plain format: a header line, the first
date, then one value per line in centavos, one line per calendar day. Days are
contiguous, so no date is stored after the first; an empty line is a day the
source has no value for, and it is never interpolated. Parsing is a line read
and an integer conversion, which matters because it runs as the page opens.

The dollar is published on business days only, so roughly two lines in seven
are empty. That is exactly what an empty line already meant, so the dollar
needed no second format and no second parser.

Both series are served straight from `web/public/`, cached by the service
worker on install, and held in memory once parsed. Neither is written to
storage: they are regenerated into the files daily, so a copy in the browser
would only be a stale duplicate of something the page already has.

A refresh only ever asks for the years the cache does not already cover,
normally just the current one, and concurrent refreshes are coalesced so two
screens opening at the same moment issue one request.

## Validated, not trusted

The public feed is not clean. It serves `608,15` for 2014-12-29 and `607,38`
for 2014-12-30, where the real UF was about 24.627. Both the generator and the
running app therefore reject any value that moves more than 1 % per elapsed
day, roughly four times the largest genuine daily change in the whole series
(0,2633 %).

Rejected days, and any the source simply omits, are then **reconstructed from
their own re-adjustment period**. Inside a period, which runs from the 10th of
one month to the 9th of the next, the UF grows at a constant daily factor by
construction, so a missing day is a term of a known geometric progression
rather than a guess. December 2014 is the clearest case: the UF was frozen at
24.627,10 for that whole period because November's CPI was 0,0 %, so the two
corrupted days recover their exact published value. Reconstruction is refused
when a gap straddles a period boundary, where the factor changes.

The result is a complete UF series with no missing day. The dollar series is
held to the same movement check but is never reconstructed: a Saturday has no
dólar observado, and inventing one would be inventing a rate people might
quote.

The tests read the shipped files directly. `seed.test.ts` and `usdSeed.test.ts`
check that every value is positive, that no day-over-day jump exceeds the
bound, that the December 2014 values are right, that there is no missing UF
day, and that the series still reproduces the monthly CPI the INE published for
2024. A corrupted regeneration fails the build, not the browser.

## Regenerating the bundled series

```bash
export CMF_API_KEY=...      # optional; without it, mindicador.cl is used
python3 -m unittest discover -s tools -p "test_*.py"
python3 tools/build_uf_daily_seed.py
python3 tools/build_usd_daily_seed.py
cd web && npm test          # the seed tests must pass before the change lands
```

Both generators pull through `tools/sources.py`, refuse to write a truncated
series and print every value they reject, along with which source answered for
each year. That last line is also written into the file's own header, so the
provenance of a shipped series is recorded rather than assumed. The same two commands run every morning in
`.github/workflows/refresh-data.yml` at 12:00 UTC, after the Banco Central has
published the day's value. The workflow validates the regenerated files with
the test suite and commits them only when something changed. Because the files
live in `web/public/`, that commit redeploys the app by itself.

## What is stored on the device

| Data | Where |
|---|---|
| UF and dollar series, companion indicators | Memory, from the bundled files |
| Saved simulations | `localStorage` |
| Theme, layout preferences, dismissed cards | `localStorage` |

Nothing else. There is no identifier, no analytics event and no account.
