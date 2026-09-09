# Architecture

Tickers ships twice: an Android app and a web app. They are two
implementations, not one codebase compiled twice, and this document describes
each, the decisions they share, and the machinery that keeps them one product.
The contract between them is in [Parity](../shared/PARITY.md).

## The shape of both channels

Both apps have the same three layers, and the boundary between them is the
same on each side:

```
ui/       screens and their state       Compose + ViewModels    Preact + hooks
domain/   pure calculation, no I/O      Kotlin, no Android      TypeScript, no DOM
data/     sources, storage, refresh     Retrofit, Room, DataStore   fetch, localStorage
```

`domain/` is where every number is made, and it has no platform imports on
either side. That is what makes the engines testable without a device or a
browser, and what makes the golden vectors possible: the same inputs go into
both implementations and the outputs are compared as strings.

The engines, one for one:

| Concern | Kotlin | TypeScript |
|---|---|---|
| UF: current value, deltas, chart windows, downsampling, summaries | `UfEngine` | `ufEngine.ts` |
| Date lookup with a closed set of outcomes | `UfLookup` | `ufLookup.ts` |
| Restating an amount between dates | `UfReajuste` | `ufReajuste.ts` |
| Rejecting implausible values, reconstructing gaps | `UfSanity` | `ufSanity.ts` |
| Dollar conversions | `FxEngine` | `fx.ts` |
| Bitcoin conversions, chart plan, failure wording | `BtcEngine` | `btc.ts` |
| Three-field converters | `ConverterEngine` | `converter.ts` |
| Mortgage schedule, prepayments, CAE | `MortgageEngine` | `mortgageEngine.ts` |

## Android

```
Kotlin 2.0 · Jetpack Compose (Material 3) · MVVM · unidirectional state
│
├── ui/
│   ├── value/       UF today, converter, history, lookup, future values (one screen)
│   ├── usd/         Dólar observado
│   ├── btc/         Bitcoin
│   ├── inflation/   The restatement calculator
│   ├── credit/      Simulation list, editor, payment table, CSV export
│   ├── components/  The history chart card, fields, pills, headers
│   ├── about/       Independence notice, sources, disclaimer
│   ├── nav/         The five destinations, in two groups
│   └── theme/
├── domain/
│   ├── model/       Data classes; DatedValue is a day and an amount
│   └── engine/      The eight engines above
├── data/
│   ├── remote/      Retrofit + OkHttp + kotlinx.serialization
│   │                CMF (primary, needs a key) → mindicador.cl (fallback); the bitcoin chain
│   ├── local/       Room: uf_values · indicators · simulations
│   ├── seed/        Parsers for the bundled series
│   ├── prefs/       DataStore: theme, last sync
│   └── repo/        Cache-first repositories
├── core/            Formatting, locale, the share sheet
├── work/            WorkManager daily sync
└── di/              A hand-written dependency container
```

Reads always come from Room, so every screen renders offline. The network is
only ever a way to refresh that cache; a failed refresh degrades to a visible
staleness warning, never to an empty screen or a fabricated number. WorkManager
is initialised on demand and its scheduling is wrapped so that background sync,
a convenience, can never prevent the app from starting.

**Manual DI instead of Hilt.** The object graph is single-level and shallow. A
hand-written container removes an annotation processor, a plugin and an entire
class of build failures, at the cost of about forty lines.

**A hand-drawn chart instead of a charting library.** The app needs one line,
one gradient fill, two endpoint labels and a scrub cursor. A dependency for
that would cost more in size and API surface than it saves. The minified
release APK is about 1,8 MB.

**Numeric fields group thousands as you type.** The field's state stays raw and
only the display is grouped, so the separator is never something the user has
to type, and a typed "." or "," is always the decimal separator. Android's
numeric keypad offers one or the other depending on the phone's locale, not
the app's.

## Web

```
TypeScript · Preact · Vite · Vitest · big.js · a service worker
│
├── src/ui/          One view per tab, the history card, the install card, share
├── src/domain/      The eight engines, money and dates
├── src/data/        mindicador.cl, the bitcoin chain, the seed parser, localStorage
├── src/core/        Formatting
├── public/          manifest, icons, the service worker, the two series (copied in)
└── scripts/         sync-data.mjs: copies the series from the Android assets
```

The web app is a static site: no server of its own, no build-time secret, and
data read either from the bundled files or from endpoints that send CORS
headers. It is deployed to GitHub Pages by `deploy-web.yml` on every push that
touches `web/` or the series, after the test suite has passed.

**The service worker** caches the app shell and both series on install, so the
page opens and every chart and date works with no connection. Requests to the
price APIs go to the network first and are never cached: a stale price is
worse than no price, and the bundled series already answers when the network
does not. The page itself goes to the network first and falls back to the
cache, because its script tag names a content-hashed bundle and a cached page
from an older deploy would ask for a file that no longer exists.

**Layout.** On a phone the five tabs sit in a bottom bar, in two groups: the
three indicators, then the two tools. On a wide screen the same navigation
becomes a collapsible rail and the cards flow into two columns; the bitcoin
view and the simulation list have their own arrangements because their cards
relate to each other differently. It is the same app at a different size, not
a different app.

**Installing.** On a phone or a tablet the app offers to put itself on the
home screen, with the steps the platform actually needs: one tap where the
browser can install, two named taps on iOS Safari. On a laptop or a desktop it
asks for a bookmark instead and names the shortcut, because that is what
people do to come back to a site there.

**Sharing.** Every card has a share button that sends the card as text, and
only the card: what is on it and nothing more. On a phone or a tablet the text
goes through the share sheet; on a laptop or a desktop it is copied, even in
browsers that offer a share panel there, because the natural next step on a
computer is pasting. The button says which it is about to do.

## Keeping the two one app

Three mechanisms, all in the repository:

1. **Golden vectors**, `shared/golden/*.json`. Inputs and exact expected
   outputs for the mortgage engine, the UF engine, bitcoin and the converters.
   Neither side generates the files; both suites read them and must match to
   the last decimal, string equality and not a tolerance. Every reported
   miscalculation arrives as a new case before it is fixed.
2. **One copy of the data.** Both series live in the Android assets and are
   copied into the web build. The daily refresh updates both channels from the
   same commit.
3. **CI on the same commit**, `.github/workflows/checks.yml`. The Android unit
   tests and the web tests run on every push and every pull request. An engine
   change on one side that is not mirrored on the other turns the build red
   rather than shipping two answers to the same question.

The version string is the same on both sides, `versionName` in
`app/build.gradle.kts` and `version` in `web/package.json`, and a release
bumps both.

## Building and releasing

**Web:** Node 22. `npm ci`, then `npm run dev` for a dev server or `npm run
build` for `web/dist`. Both first run `sync-data`, which copies the series in
from the Android assets, then `build` type-checks before bundling.

**Android:** JDK 17 and Android SDK Platform 35. `./gradlew assembleDebug`
produces an installable APK; debug builds use the `.debug` application ID
suffix so they install alongside a release. `./gradlew assembleRelease` builds
the minified release, signed when a `keystore.properties` at the project root
(gitignored) names the keystore:

```properties
storeFile=tickers-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release variant still builds, unsigned. Releases are
published on GitHub with the APK attached, which is what the download link in
the README and Obtainium follow.

**Data:** the two generators in `tools/` and the daily workflow are described
in [Data](DATA.md#regenerating-the-bundled-series). **Icons:** every icon on
both channels is generated from one script, `tools/build_icons.py`; see
[Design](DESIGN.md).

## Project layout

```
Tickers/
├── app/                         Android
│   ├── src/main/assets/         uf_daily.txt · usd_daily.txt, the bundled series
│   ├── src/main/java/cl/tickers/app/
│   ├── src/test/                JVM unit tests, including the parity suites
│   └── src/androidTest/         Instrumented tests
├── web/                         The web app
│   ├── src/                     ui · domain · data · core
│   ├── public/                  manifest, icons, service worker
│   └── scripts/sync-data.mjs
├── shared/
│   ├── golden/                  The fixtures both channels must match
│   └── PARITY.md                The contract between the channels
├── tools/
│   ├── build_uf_daily_seed.py   Regenerates the UF series
│   ├── build_usd_daily_seed.py  Regenerates the dollar series
│   └── build_icons.py           Generates every icon from one description
├── docs/                        This documentation, and the screenshots
└── .github/workflows/
    ├── checks.yml               Both test suites, every commit
    ├── deploy-web.yml           GitHub Pages
    └── refresh-data.yml         The daily data refresh
```
