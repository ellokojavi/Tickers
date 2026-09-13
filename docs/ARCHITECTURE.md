# Architecture

Tickers is a static web app: no server of its own, no build-time secret, and
data read either from files that ship with the page or from endpoints that send
CORS headers. This document describes its shape, the decisions behind it, and
the machinery that keeps its numbers honest.

## The three layers

```
ui/       screens and their state       Preact + hooks
domain/   pure calculation, no I/O      TypeScript, no DOM
data/     sources, storage, refresh     fetch, localStorage
```

`domain/` is where every number is made, and it has no DOM and no `fetch` in
it. That is what makes the engines testable without a browser, and what makes
the golden fixtures possible: inputs go in, and the outputs are compared as
strings.

The engines:

| Concern | Module |
|---|---|
| UF: current value, deltas, chart windows, downsampling, summaries | `ufEngine.ts` |
| Date lookup with a closed set of outcomes | `ufLookup.ts` |
| Restating an amount between dates | `ufReajuste.ts` |
| Rejecting implausible values, reconstructing gaps | `ufSanity.ts` |
| Dollar conversions | `fx.ts` |
| Bitcoin conversions, chart plan, failure wording | `btc.ts` |
| Three-field converters | `converter.ts` |
| Mortgage schedule, prepayments, CAE | `mortgageEngine.ts` |

## The layout

```
TypeScript · Preact · Vite · Vitest · big.js · a service worker
│
├── src/ui/          One view per tab, the history card, the install card, share
├── src/domain/      The eight engines, money and dates
├── src/data/        mindicador.cl, the bitcoin chain, the seed parser, localStorage
├── src/core/        Formatting
├── golden/          The fixtures the suite pins every figure against
├── public/          manifest, icons, the service worker, the two bundled series
└── scripts/         screenshots.mjs
```

**A hand-drawn chart instead of a charting library.** The app needs one line,
one gradient fill, two endpoint labels and a scrub cursor. A dependency for
that would cost more in bundle size and API surface than it saves. The whole
bundle is under 100 kB gzipped.

**One history card.** Every history chart in the app — UF, dollar, bitcoin —
is drawn by `ui/HistoryCard.tsx` and reads its summary from `chartSummary` in
the domain. A chart that needs something the card lacks gets the card extended
for everything, never a local exception. The three had quietly drifted apart
once: different heights, different chip spellings, and one of them moved the
headline while scrubbing.

**Numeric fields group thousands when they lose focus.** The field's state
stays raw and only the display is grouped, so the separator is never something
the user has to type, and a typed "." or "," is always the decimal separator —
which one a phone's numeric keypad offers depends on the phone's locale, not
the app's. Grouping on blur rather than on every keystroke means there is no
caret to map across inserted separators, which is where this class of field
usually goes wrong.

**The service worker** caches the app shell and both series on install, so the
page opens and every chart and date works with no connection. Requests to the
price APIs go to the network first and are never cached: a stale price is
worse than no price, and the bundled series already answers when the network
does not. The page itself goes to the network first and falls back to the
cache, because its script tag names a content-hashed bundle and a cached page
from an older deploy would ask for a file that no longer exists.

**Routing through the hash.** Every screen has its own address — `#/uf`,
`#/bitcoin`, the bare hash for the overview — in about forty lines and with no
dependency. The hash rather than the path because the app is a static file:
GitHub Pages has no rewrite rule to send an unknown path back to index.html,
and the service worker would have to grow one too. Navigation is by `<a href>`,
so every move lands in the browser's history and the back button does what it
looks like it does. An unknown hash resolves to the overview, never to nothing.

**Layout.** On a phone the bottom bar holds the overview and the three
indicators, with the two tools in a menu behind its fifth slot. On a wide
screen the same navigation becomes a collapsible rail with both groups shown in
full, and the cards flow into two columns; the overview, the bitcoin view and
the simulation list have their own arrangements because their cards relate to
each other differently. It is the same app at a different size, not a different
app.

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

## Keeping the numbers honest

Two mechanisms, both in the repository:

1. **Golden fixtures**, `web/golden/*.json`. Inputs and exact expected outputs
   for the mortgage engine, the UF engine, bitcoin and the converters, checked
   as strings rather than within a tolerance. They are never generated from the
   engines: a fixture the engine wrote would agree with whatever the engine
   currently does. Changing a figure on purpose means editing the file
   deliberately, which is exactly the moment worth pausing at, and every
   reported miscalculation arrives as a new case before it is fixed.
2. **CI on every commit**, `.github/workflows/checks.yml`, which runs the type
   check and the suite on every push and every pull request.

## Building and deploying

Node 22. `npm ci`, then `npm run dev` for a dev server or `npm run build` for
`web/dist`; `build` type-checks before bundling. The version string lives in
`web/package.json` and is read into the app at build time.

`deploy-web.yml` publishes to GitHub Pages on every push that touches `web/`,
after the suite has passed. Because the bundled series live in `web/public/`,
the daily data refresh redeploys the app by itself.

The data generators in `tools/` and the daily workflow are described in
[Data](DATA.md#regenerating-the-bundled-series). **Icons:** every icon is
generated from one script, `tools/build_icons.py`; see [Design](DESIGN.md).

## Project layout

```
Tickers/
├── web/
│   ├── src/                     ui · domain · data · core
│   ├── golden/                  The fixtures every figure is pinned against
│   ├── public/                  manifest, icons, service worker, the two series
│   └── scripts/screenshots.mjs
├── tools/
│   ├── build_uf_daily_seed.py   Regenerates the UF series
│   ├── build_usd_daily_seed.py  Regenerates the dollar series
│   └── build_icons.py           Generates every icon from one description
├── docs/                        This documentation, and the screenshots
└── .github/workflows/
    ├── checks.yml               Types and tests, every commit
    ├── deploy-web.yml           GitHub Pages
    └── refresh-data.yml         The daily data refresh
```
