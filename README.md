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

### Today

- Current UF value in Chilean pesos, with the exact publication date.
- Change versus the previous day (absolute) and versus 30 days ago (percentage).
- Two-way UF ⇄ CLP converter.
- 60-day sparkline.
- Companion indicators: IVP, US dollar, euro, UTM, monthly CPI.
- An explicit **data source badge** — the app never hides where a number came
  from, and marks whether that source is official.
- Freshness indicator, and a visible warning when showing cached data.

### History

- Full daily series back to **August 1977**.
- Range selector: 1M / 3M / 6M / 1Y / 5Y / Max, with on-demand backfill of older
  years.
- Interactive chart with touch scrubbing.
- Date lookup for any single day.
- **Published future values are drawn as a dashed segment and labelled as
  official, never as a projection.** When a requested date lies beyond the
  published horizon, the app says so rather than extrapolating.

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
- Complete payment table: opening balance, interest, principal, dividend, life
  insurance, fire insurance, prepayment, monthly total and closing balance —
  every row in UF, with peso equivalents at the current UF value.
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
| RF-6 | Display already-published future UF values, explicitly marked as official |
| RF-7 | Never extrapolate or project a UF value that has not been published |
| RF-8 | Chart the series over selectable ranges with touch scrubbing |
| RF-9 | Restate an amount between any two months using the CPI index |
| RF-10 | Show the same restatement expressed in UF units |
| RF-11 | Report adjustment factor, cumulative inflation and annualised rate |
| RF-12 | Reject out-of-coverage dates with an explanation |
| RF-13 | Simulate a `UF + x%` mortgage and produce a full amortisation schedule |
| RF-14 | Support both annual→monthly rate conventions used in Chile |
| RF-15 | Model life and property insurance, fees, stamp tax and upfront costs |
| RF-16 | Compute the CAE from actual cash flows |
| RF-17 | Model prepayments, reducing either term or payment |
| RF-18 | Create, read, update, duplicate and delete saved simulations |
| RF-19 | Persist simulations across app restarts and updates |
| RF-20 | Export a schedule to CSV and share it |
| RF-21 | Refresh data daily in the background and on manual pull |
| RF-22 | Operate fully offline from cached and bundled data |
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

---

## Data sources

| Source | Role | Key | Notes |
|--------|------|-----|-------|
| [CMF Chile](https://api.cmfchile.cl) | **Primary — official** | Required, free | The financial regulator's own API. Quota: 10,000 requests/month |
| [mindicador.cl](https://mindicador.cl) | Fallback | None | Third-party mirror of Banco Central data |
| Bundled asset | Offline seed | — | Monthly UF anchors, Aug 1977 → present |

The app tries the official source first and falls back automatically. Because
one refresh per day is enough — the UF for the entire current period is already
published — the CMF quota comfortably covers normal use.

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
python3 tools/build_uf_seed.py
```

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
├── domain/      Pure Kotlin. No Android imports.
│   ├── model/   Data classes
│   └── engine/  UfEngine · InflationEngine · MortgageEngine
├── data/
│   ├── remote/  Retrofit + OkHttp + kotlinx.serialization
│   │            CmfDataSource (primary) → MindicadorDataSource (fallback)
│   ├── local/   Room: uf_values · indicators · simulations
│   ├── seed/    Bundled UF anchor dataset
│   ├── prefs/   DataStore settings
│   └── repo/    Cache-first repositories
├── work/        WorkManager daily sync
└── di/          Hand-written dependency container
```

**Two decisions worth stating:**

*Manual DI instead of Hilt.* The object graph is single-level and shallow. A
hand-written container removes an annotation processor, a plugin, and an entire
class of build failures, at the cost of about forty lines.

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
│   │   ├── assets/uf_monthly_seed.json    Bundled UF anchors (1977→)
│   │   ├── java/cl/ufchile/app/           Source
│   │   └── res/                           Resources
│   ├── src/test/                          JVM unit tests
│   └── src/androidTest/                   Instrumented tests
├── tools/build_uf_seed.py                 Regenerates the bundled dataset
├── README.md
└── TESTING.md
```

---

## Roadmap

**v0.1 — current**
Today, History, Inflation, full mortgage simulator with CRUD and CSV export.
68 tests passing; debug and minified release builds verified on an emulator.

**Toward v1.0**
- PDF export of payment schedules
- Side-by-side comparison of up to three simulations
- Validation of the default rate convention against published bank quotes
- Home-screen widget
- Instrumented UI test coverage on the four main flows

**Considered, not committed**
UF change notifications; UTM and tax calculators; iOS.

---

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
