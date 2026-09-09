# Data

Where every number in Tickers comes from, what the sources allow, how the data
is validated and repaired, and how it is kept fresh. The short version: the
complete UF and dollar series ship inside both apps and are regenerated every
morning; the network only ever refreshes the day's value; nothing is stored
without a plausibility check; and nothing is ever invented.

## Sources

| Series | Source | Used by | Notes |
|---|---|---|---|
| UF, live | [CMF Chile](https://api.cmfchile.cl) | Android | **Official.** The financial regulator's own API. Needs a free key; quota 10.000 requests a month |
| UF, live | [mindicador.cl](https://mindicador.cl) | Android (fallback), web | Third-party mirror of Banco Central data. Sends CORS headers, which is what lets a static page read it |
| UF, bundled | `app/src/main/assets/uf_daily.txt` | Both | The complete daily series, 1 August 1977 to the day of the last refresh: about 18.000 days, some 50 kB compressed |
| Dólar observado, bundled | `app/src/main/assets/usd_daily.txt` | Both | Every business day since 2 January 1984, about 10.600 published values. Weekends and holidays are blank lines |
| Companion indicators | mindicador.cl | Both | IVP, dólar, euro, UTM and monthly IPC, fetched with the day's UF |
| Bitcoin, spot | Coinbase, CoinGecko, Kraken, and on Android also Binance | Both | Tried in order; the first answer wins. Binance sends no CORS header, so a browser cannot read it |
| Bitcoin, candles | Coinbase Exchange | Both | One request per horizon, at the candle width the horizon calls for |

The Android app tries the CMF first and falls back to mindicador.cl. The web app
uses mindicador.cl only: the CMF key cannot be kept secret inside a public page.
Neither app depends on the network being up. Both open on the bundled series,
which is regenerated daily, so the page is never more than a day behind even
when every endpoint is unreachable.

Every screen names its source. The UF card carries a badge saying whether the
value came from the CMF, from mindicador.cl, from the bundled data or from the
local cache, and whether that source is official. The bitcoin card names the
exchange that answered and the date of the dollar used to convert it.

### Terms, and what they require

The CMF's [terms of use](https://api.cmfchile.cl/terminos-de-uso.html)
authorise publishing its data in a third-party application on one condition:
the source must be named, **with a link to its site**, wherever the data is
republished. Both apps satisfy this in their "Acerca de" screen, which names
the CMF and links to cmfchile.cl. That is a requirement, not a courtesy.

mindicador.cl publishes **no terms of use at all**: no licence, no stated
permission to redistribute, no rate limits. It mirrors Banco Central data and
is credited in the same screen, but its legal position is undefined, which is
why it is the fallback on Android rather than the primary. Anyone shipping the
Android app should configure a CMF key so it runs on the source that explicitly
authorises what the app does.

The bitcoin sources are public price endpoints used within their published
limits, at most one request every thirty seconds for the spot price and one per
horizon change for the chart, and each is named on screen when it answers.

### Configuring the CMF key

Request a free key at [api.cmfchile.cl](https://api.cmfchile.cl) and add it to
`local.properties`, which is not committed:

```properties
CMF_API_KEY=your_key_here
```

Leaving it blank is supported: the Android app reports the CMF source as
unavailable and uses the fallback. The key is injected at build time through
`BuildConfig` and never appears in version control.

## The bundled series

Both series use the same deliberately plain format: a header line, the first
date, then one value per line in centavos, one line per calendar day. Days are
contiguous, so no date is stored after the first; an empty line is a day the
source has no value for, and it is never interpolated. Parsing is a line read
and an integer conversion, which matters because it runs at startup on a phone.

The dollar is published on business days only, so roughly two lines in seven
are empty. That is exactly what an empty line already meant, so the dollar
needed no second format and no second parser.

The Android app loads the UF series into Room on first launch, off the main
thread, in well under a second, and writes newly published values back into the
same table as they arrive. The dollar series is kept in memory on both
channels: it is regenerated into the asset daily, so a table would only be a
copy of a file. The web app copies both files from the Android assets at build
time (`web/scripts/sync-data.mjs`), so neither channel has a copy that can fall
behind.

A refresh only ever asks for the years the cache does not already cover,
normally just the current one, and concurrent refreshes are coalesced so the
background worker and the screen opening at the same moment issue one request.
The CMF quota is never under pressure.

## Validated, not trusted

The public feed is not clean. It serves `608,15` for 2014-12-29 and `607,38`
for 2014-12-30, where the real UF was about 24.627. Both the generator and the
running apps therefore reject any value that moves more than 1 % per elapsed
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

The tests read the shipped files directly. `UfDailySeedParseTest` on Android and
`seed.test.ts` on the web check that every value is positive, that no
day-over-day jump exceeds the bound, that the December 2014 values are right,
that there is no missing UF day, and that the series still reproduces the
monthly CPI the INE published for 2024. A corrupted regeneration fails the
build, not the phone.

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
the web test suite and commits them only when something changed. That commit
redeploys the web app, and every Android release built afterwards ships the
series up to date.

## What is stored on the device

| Data | Android | Web |
|---|---|---|
| UF series and companion indicators | Room | Memory, from the bundled file |
| Dollar series | Memory | Memory |
| Saved simulations | Room | `localStorage` |
| Theme, layout preferences, dismissed cards | DataStore | `localStorage` |
| Last sync source and time | DataStore | not kept |

Nothing else. There is no identifier, no analytics event and no account. On
Android the manifest declares `INTERNET` and `ACCESS_NETWORK_STATE`, plus the
normal-level permissions WorkManager merges in for its scheduling; none is a
runtime permission and the app never shows a permission prompt.
