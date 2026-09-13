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
| Companion indicators | mindicador.cl | IVP, dólar, euro, UTM and monthly IPC, fetched with the day's UF |
| Bitcoin, spot | Coinbase, CoinGecko, Kraken | Tried in order; the first answer wins |
| Bitcoin, candles | Coinbase Exchange | One request per horizon, at the candle width the horizon calls for |

The app does not depend on the network being up. It opens on the bundled
series, which is regenerated daily, so a first load is never more than a day
behind even when every endpoint is unreachable.

Every screen names its source. The UF card carries a badge saying whether the
value came from mindicador.cl, from the bundled data or from the local cache,
and whether that source is official. The bitcoin card names the exchange that
answered and the date of the dollar used to convert it.

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

**It is now the only live source, and that is worth stating plainly.** The app
used to call the CMF's own API — the source that explicitly authorises what
this app does — but that path needed a key, and a key cannot be kept secret
inside a public page, so it only ever existed on the Android channel. When that
channel was removed in September 2026 the CMF path went with it. The bundled
series, which is where almost every figure the app shows actually comes from,
is generated from mindicador.cl too. Moving the daily generators onto an
authorised source, or onto the Banco Central's own published series, is the
obvious way to close this and is not done.

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
python3 tools/build_uf_daily_seed.py
python3 tools/build_usd_daily_seed.py
cd web && npm test          # the seed tests must pass before the change lands
```

Both generators pull from mindicador.cl, refuse to write a truncated series and
print every value they reject. The same two commands run every morning in
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
