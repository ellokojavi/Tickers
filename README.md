# UF Chile

An Android app for working with Chile's **Unidad de Fomento (UF)** — the
inflation-indexed accounting unit that prices most of the country's mortgages,
rents, insurance policies and long-term contracts.

It answers four questions:

1. What is the UF worth today?
2. What was it worth on any past date — and what will it be worth on the days
   already officially published into the future?
3. What is an amount from another era worth in today's money?
4. What does a `UF + x%` mortgage actually cost, month by month?

Offline-first, no account, no tracking, no ads.

---

## Table of contents

- [Why this exists](#why-this-exists)
- [Features](#features)
- [Requirements](#requirements)
- [Data sources](#data-sources)
- [How the inflation index is derived](#how-the-inflation-index-is-derived)
- [Mortgage model](#mortgage-model)
- [Architecture](#architecture)
- [Building](#building)
- [Testing](#testing)
- [Project layout](#project-layout)
- [Roadmap](#roadmap)
- [Disclaimer](#disclaimer)
- [License](#license)

---

## Why this exists

The UF changes **every single day**, and its value for the coming weeks is
already published — a fact most conversion tools ignore, either by showing only
today's value or by extrapolating one. Meanwhile, comparing prices across
decades in Chile requires splicing CPI series, and mortgage quotes arrive as a
single monthly figure with the insurance, taxes and fees folded invisibly into
it.

This app treats all three as the same problem: making an indexed unit legible.

---

## Features

### UF value

One screen, because "what is the UF worth today" and "what was it worth then"
are the same question at different points in time.

- Current UF value in Chilean pesos, with the exact publication date.
- Change versus the previous day (absolute) and versus 30 days ago (percentage).
- Two-way UF ⇄ CLP converter. The peso side is whole pesos: Chile has not used
  centavos for decades. The headline keeps its two decimals because the UF
  itself is published that way.
- **Share the card** as preformatted text through the system share sheet —
  WhatsApp, Telegram, mail, notes, anywhere.
- **The history is always on screen**, not a mode to switch into: range
  selector (1M / 3M / 6M / 1Y / 5Y / Max), the change over that range both
  cumulative and annualised, and a chart whose **two ends are labelled with
  their date and value**, so it can be read without touching it.
- Touch scrubbing. **The headline value never changes while scrubbing** — the
  explored day is reported separately, so the screen cannot misstate what the
  UF is worth today.
- Date lookup for any single day, with the calendar **bounded by the published
  horizon**: a day whose UF does not exist yet cannot be selected at all, and
  the lookup never answers with a neighbouring day's value.
- Already-published future values, explained and marked as official.
- A day-by-day list of the selected range, **collapsed by default** — it runs
  to hundreds of rows and is a reference, not the main event.
- Companion indicators: IVP, US dollar, euro, UTM, monthly CPI.
- An explicit **data source badge** — the app never hides where a number came
  from, and marks whether that source is official.
- Freshness indicator, and a visible warning when showing cached data.

Published future values are drawn as a dashed segment and labelled as official,
never as a projection. When a requested date lies beyond the published horizon,
the app says so rather than extrapolating.

### Inflation

- Restates an amount between any two months, in either direction.
- Two independent readings shown side by side:
  - via the **CPI index** (the canonical answer),
  - via **UF units** (how many UF the amount bought then, priced today).
- Reports the adjustment factor, cumulative inflation and the equivalent annual
  rate.
- Coverage: **October 1977 to the present**. Months outside that window are
  rejected with an explanation instead of being guessed. Dates before the
  current Chilean peso (1975) are out of scope by design.

### Mortgage simulator

- `UF + x%` loans on the French amortisation system (constant capital + interest
  payment).
- **A user-chosen due date for the first instalment**, so the schedule carries
  real dates rather than an assumed "one month from today". Later instalments
  fall on the same day each month, clamped to the last day of shorter months
  exactly as a lender would.
- Complete payment table: opening balance, interest, principal, dividend, life
  insurance, fire insurance, prepayment, monthly total and closing balance —
  every row in UF, with peso equivalents at the current UF value.
- Opening a **saved** simulation leads with the result and the payment table;
  the editable form and its update action sit below. A **new** simulation leads
  with the form. Consulting and creating are different jobs.
- Cost inputs: *desgravamen* (percentage of the outstanding balance),
  *incendio y sismo* (fixed monthly UF), origination fee, stamp tax
  (*impuesto de timbres*), and notary/appraisal/registry costs.
- **CAE** (*Carga Anual Equivalente*) solved from the real cash flows.
- Prepayments (*abonos a capital*), either shortening the term or lowering the
  payment.
- **Both rate conventions** are supported — see [Mortgage model](#mortgage-model).
- CSV export of the full schedule, shared through the Android share sheet.

### Saved simulations

Create, read, update, duplicate and delete named simulations. Persisted locally
in Room; deletions offer an undo. Nothing leaves the device.

---

## Requirements

### Functional

| ID | Requirement |
|----|-------------|
| RF-1 | Display today's official UF value with its publication date |
| RF-2 | Display daily change and 30-day percentage change |
| RF-3 | Convert UF ⇄ CLP in both directions |
| RF-4 | Display companion indicators (IVP, USD, EUR, UTM, CPI) |
| RF-5 | Query the UF value for any date within coverage |
| RF-5b | Present today's value and the historical series as one destination, with the history always visible |
| RF-5c | Refuse to answer for a date with no published value, and make such dates unselectable |
| RF-6 | Display already-published future UF values, explicitly marked as official |
| RF-7 | Never extrapolate or project a UF value that has not been published |
| RF-8 | Chart the series over selectable ranges with touch scrubbing, without ever altering the headline value |
| RF-8b | Report the range's change both cumulatively and annualised |
| RF-8c | Label both ends of the chart with their date and value |
| RF-8d | Keep the day-by-day list collapsed until requested |
| RF-9 | Restate an amount between any two months using the CPI index |
| RF-10 | Show the same restatement expressed in UF units |
| RF-11 | Report adjustment factor, cumulative inflation and annualised rate |
| RF-12 | Reject out-of-coverage dates with an explanation |
| RF-13 | Simulate a `UF + x%` mortgage and produce a full amortisation schedule |
| RF-13b | Let the user set the first instalment's due date and derive every later date from it |
| RF-14 | Support both annual→monthly rate conventions used in Chile |
| RF-15 | Model life and property insurance, fees, stamp tax and upfront costs |
| RF-16 | Compute the CAE from actual cash flows |
| RF-17 | Model prepayments, reducing either term or payment |
| RF-18 | Create, read, update, duplicate and delete saved simulations |
| RF-18b | Open a saved simulation on its result; open a new one on its form |
| RF-19 | Persist simulations across app restarts and updates |
| RF-20 | Export a schedule to CSV and share it |
| RF-20b | Share today's UF card as preformatted text via the system share sheet |
| RF-21 | Refresh data daily in the background and on manual pull |
| RF-22 | Operate fully offline from cached and bundled data |
| RF-22b | Ship the complete daily series and refresh only what is genuinely new |
| RF-22c | Reject implausible values from any source rather than storing them |
| RF-23 | Label the origin of every value and whether the source is official |

### Non-functional

| ID | Requirement |
|----|-------------|
| RNF-1 | Android 8.0+ (minSdk 26), targetSdk 35 |
| RNF-2 | Spanish (Chile) throughout; `es-CL` number formatting (`$40.880,36`) |
| RNF-3 | Functional with no network connection; never a blank screen |
| RNF-4 | APK under 15 MB — the minified release build is **1.6 MB** |
| RNF-5 | Cold start under 1.5 s |
| RNF-6 | No analytics, no account, no personal data; `INTERNET` is the only sensitive permission |
| RNF-7 | All monetary arithmetic in `BigDecimal`; `Double` is never used for money |
| RNF-8 | WCAG AA contrast, font-scaling support, TalkBack labels |
| RNF-9 | Light and dark themes, following the system or set manually |
| RNF-10 | Calculation engines are pure Kotlin, testable without a device |
| RNF-11 | Chilean iconography — the flag as the app mark, the copihue and condor as illustrations — without breaking the minimalist palette |

---

## Data sources

| Source | Role | Key | Notes |
|--------|------|-----|-------|
| [CMF Chile](https://api.cmfchile.cl) | **Primary — official** | Required, free | The financial regulator's own API. Quota: 10,000 requests/month |
| [mindicador.cl](https://mindicador.cl) | Fallback | None | Third-party mirror of Banco Central data |
| Bundled asset | Offline seed | — | **The complete daily series**, Aug 1977 → build date |

The app tries the official source first and falls back automatically.

**The entire daily series ships inside the APK** — about 18.000 days from
August 1977, roughly 50 kB compressed. It is loaded into the database on first
launch (0,6 s, off the main thread), so every chart and every date lookup works
offline from the very first run, and a refresh only ever asks for the years the
cache does not already cover — normally just the current one. Concurrent
refreshes are coalesced, so the background worker and the screen opening at the
same moment issue one request, not two. The CMF quota is therefore never under
pressure.

### The data is validated, not trusted

The public feed is not clean. It serves `608,15` for 2014-12-29 and `607,38`
for 2014-12-30, where the real UF was about 24.627. Both the generator and the
running app therefore reject any value that moves more than 1% per elapsed day
— roughly four times the largest genuine daily change in the whole 49-year
series (0,2633%). Rejected days, and any the source simply omits, are then **reconstructed from
their own re-adjustment period**. Inside a period — the 10th of one month to
the 9th of the next — the UF grows at a constant daily factor by construction,
so a missing day is a term of a known geometric progression rather than a
guess. December 2014 is the clearest case: the UF was frozen at 24.627,10 for
that entire period because November's CPI was 0,0%, so the two corrupted days
recover their exact published value. Reconstruction is refused when a gap
straddles a period boundary, where the factor changes.

The result is a complete series: 17.937 days with no holes.

### Configuring the CMF API key

Request a free key at [api.cmfchile.cl](https://api.cmfchile.cl), then add it to
`local.properties` (which is **not** committed):

```properties
CMF_API_KEY=your_key_here
```

Leaving it blank is supported: the app reports the CMF source as unavailable and
uses the fallback. The key is injected at build time via `BuildConfig` and never
appears in version control.

---

## How the inflation index is derived

Chile's CPI is published as a **monthly percentage with one decimal**. Chaining
those figures from 1990 to today means multiplying ~430 rounded numbers, and the
rounding error compounds.

This app does not chain them. It derives the price index from the **UF itself**.

The UF is re-adjusted daily so that:

```
UF(9th of month M+1) / UF(9th of month M) = 1 + CPI variation of month M-1
```

The UF is published to two decimals on a value in the tens of thousands, giving
roughly `1e-7` relative precision — several orders of magnitude better than a
one-decimal percentage. So the index is simply:

```
index(month m) = UF value on the 9th of month m+2
```

**This is verified, not assumed.** `InflationEngineTest` reproduces every
published monthly CPI figure for 2024 from the UF series; the deviation is
0.000 pp in all eleven months. The test runs on the exact asset file shipped
inside the APK, so a corrupted regeneration fails the build rather than the
phone.

The bundled dataset is regenerated with:

```bash
python3 tools/build_uf_daily_seed.py
```

It refuses to write a truncated series and prints every value it rejects.

---

## Mortgage model

Payments follow the French system — a constant capital-plus-interest amount:

```
payment = P · i / (1 − (1 + i)^(−n))
```

Insurance is charged **on top of** that payment and is deliberately not constant:
the *desgravamen* premium tracks the outstanding balance, so the real monthly
outflow falls over the life of the loan. The app shows both figures.

### The rate convention caveat

Chilean lenders quote an annual rate but do not all convert it to a monthly rate
the same way:

- `monthly = annual / 12` — the common convention, and the app's default
- `monthly = (1 + annual)^(1/12) − 1` — the effective equivalent, always slightly
  cheaper

Rather than pick one and silently mismatch a real bank quote, **both are
selectable in the UI**. If a simulation does not match a lender's own figure,
switching the convention is the first thing to try.

### CAE

The *Carga Anual Equivalente* is solved by bisection: the app finds the monthly
rate at which the present value of every payment the borrower makes — dividend,
insurance, prepayments — equals the loan amount net of upfront costs, then
annualises it. With no fees and no insurance it converges on the effective
annual rate, which is asserted in the test suite.

---

## Architecture

```
Kotlin · Jetpack Compose (Material 3) · MVVM · unidirectional state
│
├── ui/          Compose screens + ViewModels, StateFlow-driven
│   ├── value/   UF today + history (one screen)
│   ├── inflation/
│   └── credit/
├── domain/      Pure Kotlin. No Android imports.
│   ├── model/   Data classes
│   └── engine/  UfEngine · InflationEngine · MortgageEngine
├── data/
│   ├── remote/  Retrofit + OkHttp + kotlinx.serialization
│   │            CmfDataSource (primary) → MindicadorDataSource (fallback)
│   ├── local/   Room: uf_values · indicators · simulations
│   ├── seed/    Bundled full daily UF series
│   ├── prefs/   DataStore settings
│   └── repo/    Cache-first repositories
├── work/        WorkManager daily sync
└── di/          Hand-written dependency container
```

**Two decisions worth stating:**

*Manual DI instead of Hilt.* The object graph is single-level and shallow. A
hand-written container removes an annotation processor, a plugin, and an entire
class of build failures, at the cost of about forty lines.

*The whole series bundled instead of paged from the network.* Eighteen thousand
days cost about 50 kB compressed — less than one screenshot — and buy offline
charts, instant range switching and a fraction of the API traffic. Charts thin
the window to 400 points before drawing, because a phone cannot resolve more
and rebuilding an 18.000-segment path on every pointer event would make
scrubbing crawl.

*A hand-drawn chart instead of a charting library.* The app needs one line, one
gradient fill and a scrub cursor. A dependency for that would cost more in size
and API surface than it saves.

Reads always come from Room, so every screen renders offline. The network is
only ever a way to refresh that cache; a failed refresh degrades to a visible
staleness warning, never to an empty screen or a fabricated number.

---

## Building

**Prerequisites:** JDK 17, Android SDK Platform 35, Build-Tools 35.

```bash
git clone <repo-url>
cd UFChile
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

```bash
./gradlew installDebug          # install on a connected device
./gradlew assembleRelease       # minified, signed release build
```

Debug builds use the `.debug` application ID suffix, so they install alongside a
release build.

### Release signing

Create a keystore and a `keystore.properties` at the project root (both are
gitignored):

```properties
storeFile=ufchile-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release variant still builds, unsigned. The minified
release APK is about 1.6 MB.

---

## Testing

```bash
./gradlew test                  # JVM unit tests — no device needed
./gradlew connectedAndroidTest  # instrumented tests — needs a device or emulator
```

The engines are pure Kotlin precisely so the numerical correctness of the app
can be verified without an emulator. See [TESTING.md](TESTING.md) for the full
strategy, coverage matrix and manual QA checklist.

---

## Project layout

```
UFChile/
├── app/
│   ├── src/main/
│   │   ├── assets/uf_daily.txt            Full daily UF series (1977→)
│   │   ├── java/cl/ufchile/app/           Source
│   │   └── res/                           Resources
│   ├── src/test/                          JVM unit tests
│   └── src/androidTest/                   Instrumented tests
├── tools/build_uf_daily_seed.py           Regenerates the bundled dataset
├── README.md
└── TESTING.md
```

---

## Roadmap

**v0.1 — current**
UF value (today plus opt-in history), an inflation calculator, and a full
mortgage simulator with CRUD and CSV export. 117 tests passing; debug and
minified release builds verified on an emulator.

**Toward v1.0**
- PDF export of payment schedules
- Side-by-side comparison of up to three simulations
- Validation of the default rate convention against published bank quotes
- Home-screen widget
- Instrumented UI test coverage on the four main flows

**Considered, not committed**
UF change notifications; UTM and tax calculators; iOS.

---

## Iconography

The app mark is the **Chilean flag in a circle** with the UF's own series
climbing across it.

A launcher crops the central 72×72 of a 108×108 adaptive icon and scales it up,
so the circle is drawn at exactly that radius: on a circular mask it fills the
icon edge to edge, and on a squircle it stays a clean circle rather than being
cut into a rounded square. The source artwork separated the green line from the
red field with a glow; a vector drawable cannot blur, so a white underlay stroke
does the same job — without it the bright green vibrates against the red. The
themed (monochrome) variant drops the flag, which cannot survive a single tint,
and keeps the ascending series.

Inside the app the **copihue** (*Lapageria rosea*), Chile's national flower,
illustrates the empty simulations state, and the **condor** stands in for an
empty chart. Both were drawn and compared at real sizes first: the condor loses
its silhouette below about 48px and reads as an insect, which is why it only
appears large. The copihue crimson is decorative and never encodes a value, so
it cannot be read as the negative-change colour.

## Disclaimer

This app is an independent tool. It is not affiliated with, endorsed by, or
connected to the Comisión para el Mercado Financiero (CMF), the Banco Central de
Chile, the Instituto Nacional de Estadísticas (INE), or any bank.

Figures are provided for information only and are **not** financial advice. A
mortgage simulation is a model, not a quote: real lenders apply their own
conventions, fees and insurance pricing. Always confirm with the institution
before making a decision.

---

## License

MIT. See [LICENSE](LICENSE).
