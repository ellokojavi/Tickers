# Testing

The app's value is entirely in its numbers, so the test suites are organised
around one principle: **anything that produces a number must be verifiable
without a device, and anything a user can tap must be verified on one.** And
because the app ships twice, a third: **the two channels must produce the same
number**, which the shared golden vectors enforce.

```bash
./gradlew test                    # Android: 157 JVM tests, seconds, no device
./gradlew connectedAndroidTest    # Android: 13 instrumented tests, needs a device or emulator
cd web && npm test                # Web: 112 tests, seconds
```

`.github/workflows/checks.yml` runs the JVM and web suites on every push and
pull request.

## Layers

| Layer | Android | Web | What it protects |
|---|---|---|---|
| Pure calculation | JUnit, 73 | Vitest, 60 | Mortgage maths, due dates, UF conversions and windows, the restatement, date-lookup rules, sanity filtering |
| Parity with the other channel | JUnit, 11 | Vitest, 18 | The golden vectors: mortgage, UF, bitcoin, converters, and the version string |
| Formatting and input | JUnit, 34 | Vitest, 10 | `es-CL` output, input grouping, cursor mapping, default state |
| Data contracts and assets | JUnit and Robolectric, 20 | Vitest, 15 | Both providers' payload shapes; the bundled series, read from the shipped files |
| Persistence | Robolectric, 12 | | Room schema, type converters, CRUD |
| Share text | JUnit, 7 | | The message a card turns into |
| UI flows | Instrumented, 13 | | Navigation, offline rendering, live computation, screen ordering |

The engines live in `domain/` on both sides with no platform imports, which is
what makes the first three layers possible at all. It is an architectural
choice made for testability, not an accident.

## Parity suites

Four fixtures in `shared/golden/` hold inputs and exact expected outputs at
the engines' own scale. Each is read by a Kotlin test and a TypeScript test,
and both must match the fixture as strings, not within a tolerance.

| Fixture | Android | Web | Covers |
|---|---|---|---|
| `mortgage.json` | `GoldenVectorTest` | `goldenVectors.test.ts` | Both rate conventions, both prepayment modes, insurance and upfront costs, month-end clamping, the zero-rate divisor |
| `uf.json` | `UfGoldenVectorTest` | `ufGoldenVectors.test.ts` | Peso conversions, deltas, the restatement, the chart summary, formatting |
| `btc.json` | `BtcGoldenVectorTest` | `btcGoldenVectors.test.ts` | Bitcoin conversions, the chart plan per horizon, the wording of a failed fetch |
| `converter.json` | `ConverterGoldenVectorTest` | `converterGoldenVectors.test.ts` | The three-field converters, where a rounding mistake shows as two answers on one screen |

`parity.test.ts` additionally checks that `versionName` in
`app/build.gradle.kts` and `version` in `web/package.json` are the same
string. The fixtures caught two real formatting divergences the first time
they ran, and the converter fixture was written after a real one: one bitcoin
read 72.476.915 pesos on load and 72.476.920 after an edit.

## What each suite asserts

The Kotlin and TypeScript suites for an engine assert the same things, so each
is described once.

### Mortgage engine, 21 tests on each side

The amortisation engine is the highest-risk code in the app: it is the only
place where a silent error produces a plausible-looking wrong answer.

- The exact-decimal payment is checked against the closed-form formula
  computed independently in floating point.
- The schedule fully amortises: the final closing balance is zero, and
  principal payments sum exactly to the loan amount.
- The balance chain is internally consistent row by row, and interest equals
  opening balance times the monthly rate.
- Insurance is charged on top of the dividend, and the *desgravamen* shrinks
  as the balance does.
- Both rate conventions produce the expected monthly rates, and the effective
  conversion is always the cheaper one.
- A zero rate splits the principal evenly and charges no interest.
- A term-reducing prepayment shortens the schedule and lowers total interest; a
  payment-reducing one keeps the term and lowers later dividends; one larger
  than the balance is capped.
- **CAE with no fees converges on the effective annual rate**, an independent
  check that the bisection solver is correct, and rises once fees and
  insurance are added.
- Upfront costs are computed off the loan amount, not the property value; the
  financed percentage reflects the down payment; total cost adds every outflow.
- On dates: the first instalment falls exactly on the chosen day, instalments
  advance one month at a time, a 31st clamps to shorter months and recovers
  (31 Jan → 28 Feb → 31 Mar → 30 Apr), and **changing the due date moves no
  money**.

### UF engine, 23 tests on Android and 20 on the web

Centred on the app's defining subtlety: **the series legitimately contains
future dates.**

- "Current" ignores published future dates and returns today's value;
  "future" returns exactly the days beyond today, in order.
- Conversions round trip; a zero rate does not divide by zero; deltas carry
  the correct sign; an empty series returns nothing rather than crashing.
- Annualisation: a full year returns its own change, six months compound
  correctly, declines stay negative, short windows magnify as the arithmetic
  requires, impossible inputs are refused, and real UF movement annualises
  close to Chilean inflation.
- Downsampling keeps the endpoints and the requested size, leaves short series
  untouched and survives absurd budgets.
- **The chart window never reaches past today**, for every range including
  "Máx", while still including today itself. It once ended on the last
  published day, up to a month into the future, so the chart's final label and
  its period variation both overshot.

### Restatement, 9 tests on each side

- The amount moves by the ratio of the two UF values, and the UF-unit reading
  agrees with it.
- The case the screen opens on, $4.000 of 1 January 1990, lands where the
  fixture says, with its factor, day count and annual rate.
- The same day is a no-op; forward and back returns the original amount;
  going backwards in time shrinks it and reports a negative variation.
- **Two dates one day apart give different results**: day precision is the
  whole point.
- A billion-peso amount does not drift, because the result comes from the
  factor once rather than from rounding UF units and back.
- A non-positive UF value is refused instead of dividing by zero.

### Date lookup, 11 tests on Android and 10 on the web

Added after a real defect: asking for a date past the published horizon
answered with the last published day's value **and labelled it official**.

- A date beyond the horizon is never answered with another day's value; the
  day after the horizon is already unpublished, the horizon itself is valid.
- Today and past dates resolve exactly and are not marked as future.
- A substitute is offered only for gaps inside coverage, and is labelled.
- Dates before August 1977 are rejected; the first day of the series is not.
- Nothing cached and nothing nearby is "unavailable", never a wrong number,
  and an empty cache does not fabricate a horizon.

### Sanity filter, 9 tests on each side

- **The real 2014 corruption is rejected**: 608,15 and 607,38 between two days
  worth about 24.627 are dropped, and the surrounding days survive.
- The largest genuine daily move in the series (0,2633 %) is kept.
- The allowance scales with the gap, so a multi-day jump is judged per day.
- An anchor lets the first value of a batch be checked too; without one it is
  trusted, which is the documented limit of the guard.
- Non-positive values never pass; input order does not matter.

### The bundled series, 11 tests on Android and 15 on the web

Read from the shipped files, so a bad regeneration fails the build rather than
the phone.

- Centavos become exact two-decimal pesos with no float step; a blank line is
  a gap that still advances the calendar; a missing or unparseable header
  yields nothing rather than wrong dates; junk lines are ignored.
- The UF series has **no missing day at all**, no implausible day-over-day
  jump, survives the runtime filter untouched, carries the real December 2014
  values, **reproduces the CPI the INE published for every month of 2024**,
  and keeps the December 2008 deflation.
- The dollar series starts where the source's history does and reaches the
  present, is in date order with no repeats, holds only plausible positive
  values, keeps the September 1984 devaluation, and has gaps only where the
  market is closed.

### Formatting and input, Android 34 and web 10

`$40.880,36`, `22,23 UF`, `+3,25%`, `05-09-2026`, "hace 4 días". Parsing
accepts `40.880,36`, `$1.000`, `4,5` and `1.234 UF`; rejects `""`, `"abc"` and
`","`; formatting and parsing round trip. Counts get their separators too.

On Android, `ThousandsTransformationTest` covers live grouping: thousands
group with dots, the decimal part is untouched, a trailing separator survives
so typing can continue, the cursor maps across inserted separators and stays
in bounds for every input, **a typed dot becomes the decimal separator**, and
**a state that is already grouped is not grouped again**. `NumericDefaultsTest`
asserts, for every default the app ships, that passing it through the
sanitiser is a no-op. See the postmortem below for why both exist.

### Share text, 7 tests on Android

The text the inflation card turns into is user-visible and pure, so it is
asserted rather than eyeballed: the equivalence leads, line by line; the
arithmetic and both UF values follow; **no figure escapes unpunctuated**;
emphasis uses the marks chat apps understand; a same-day restatement is still
coherent; and **nothing that is not on the result card is included**, since
the sentence about the method belongs to the card below it.

### Data contracts, 5 tests on Android

Contract tests against captured real payloads from both providers: the CMF's
Chilean-formatted strings parse to exact decimals, mindicador's floats keep
exact cents with no binary noise, unknown fields and missing sections degrade
gracefully, empty payloads yield no values rather than an exception. If either
API changes shape, this fails here instead of silently on a phone.

### Persistence, 12 tests on Android, Robolectric

The real Room schema on the JVM. A saved simulation round trips with **no loss
of decimal precision**, the direct test of the "money is TEXT, never REAL"
rule; prepayments survive their JSON round trip; update mutates rather than
inserts and advances `updatedAt`; duplicate produces a new id with identical
inputs; delete removes only its target; the list orders by most recently
updated; upsert replaces rather than duplicates a date; `atOrBefore` resolves
backwards only; `maxDate` sees published future days. `UfSeedTest` loads the
seed through the real asset pipeline, catching a renamed or unpackaged file.

### UI flows, 13 instrumented tests on Android

Run on a device or emulator against the real activity.

- The app launches showing today's value and the converter; the tabs are
  present; the history and its range selector are visible with no interaction
  at all; the date lookup is present on arrival.
- **The day-by-day list starts collapsed** and expands on demand.
- The inflation screen computes from bundled data with no network, the direct
  test of the offline-first requirement; its dates are chosen by day; and its
  **"Hoy" shortcut appears only when the end date is not today**.
- Credits opens the editor and produces a result from defaults alone,
  including a CAE; **a saved simulation opens on its result, a new one on its
  form**; the payment table's first row carries the chosen due date.
- The theme toggle does not break the screen.
- The independence notice is reachable but never pushed.

Assertions deliberately target chrome and bundled-data content, never a value
that depends on a successful network call, so the suite is not flaky on a
machine with poor connectivity.

## Bugs the suites found

Worth recording, because each would have shipped otherwise.

1. **WorkManager crashed the app at startup under test.** `Application.onCreate`
   called `WorkManager.getInstance()`, which throws when the startup
   initializer has not run. Fixed with on-demand initialisation and by wrapping
   the scheduling: background sync is a convenience and must never prevent the
   app from starting.
2. **The "new simulation" button was invisible to screen readers.** Its label
   sits in a slot that is not merged into the node's semantics, so TalkBack
   announced only "Button". Found because the UI test could not locate it
   either. Fixed with an explicit content description.
3. **A future date was answered with another day's value, labelled official.**
   The fix is a closed set of outcomes plus a bounded calendar, and the
   regression is pinned by its own test on both channels.
4. **The public data source was serving corrupt values.** Preloading the whole
   series surfaced a 97 % single-day collapse in December 2014. Both the
   generator and the running apps now reject implausible movements, and the
   seed tests pin the specific values.
5. **Expanding the history put its own controls off screen.** The range chips
   sat below a chart that grows to 200 dp, which pushed them past the fold. The
   test failed on `assertIsDisplayed`, which is precisely the distinction that
   matters: the node existed, it just was not visible. The chips now sit above
   the chart.
6. **A converter disagreed with itself.** One bitcoin read a different peso
   figure on load than after an edit, because the pesos were being derived
   from the already-rounded dollars. The converter fixture pins every field
   against the engine.
7. **The two channels formatted differently.** ICU on Android and hand-rolled
   grouping on the web disagreed twice on the first run of the UF fixture, in
   ways that would have been as visible as a wrong figure.

## The one that escaped: a postmortem

The inflation screen rendered its amount as **`4..000`**. It shipped, and a
user found it.

**What happened.** Numeric fields hold raw text and the display adds the
grouping. That screen's default was still the pre-formatted `"4.000"`, so the
transformation counted the dot as a digit position and inserted a second one.

**Why nothing caught it.** Three separate gaps, each a general lesson:

1. *The transformation's tests only ever fed it valid input.* The one state
   that could break it, one containing a grouping character, was never tried,
   and the function produced garbage silently instead of coping or failing.
2. *The invariant was a convention, not a check.* Three defaults were changed
   by hand when grouping was introduced and a fourth, in a different
   ViewModel, was missed. Nothing asserted that defaults are raw.
3. *The UI test asserted the results, not the field.* The computation stayed
   correct the whole time, because the parser discards dots, so the app
   produced right answers behind a corrupted display and no assertion could
   fail.

There was also a process failure: the change was verified by screenshotting
the mortgage form, where the defaults had been fixed, rather than the screen
that had not been touched.

**What changed.** The default is corrected; the transformation normalises
whatever state it is handed, so no caller can make it render nonsense;
`NumericDefaultsTest` pins the invariant for every shipped default; and the
transformation is tested against dirty state. The defaults test was verified
to fail when the old value is put back. A regression test that does not fail
on the original bug is decoration.

## Manual QA checklist

Automated coverage stops at the device boundary. These are checked by hand
before a release, on both channels unless a line says otherwise.

### Data correctness
- [ ] Today's UF matches [CMF](https://www.cmfchile.cl) to the cent, and the
      dollar matches the Banco Central's published observed rate
- [ ] Future UF values extend to the 9th of next month and are labelled as
      official, never as projections
- [ ] A date before August 1977 reports missing coverage instead of a number
- [ ] On a weekend, the dollar shows the last published value with its date
- [ ] The bitcoin card names its source, and the peso figure carries the
      dollar's date
- [ ] A known inflation case is spot-checked against an external calculator
- [ ] A mortgage simulation is compared against a real bank's published
      simulator, under **both** rate conventions
- [ ] A due date on the 29th, 30th or 31st produces the expected month-end
      behaviour across a February

### Offline behaviour
- [ ] Airplane mode on first launch: every chart and date lookup works from the
      bundled series, not just the inflation calculator
- [ ] Airplane mode after a sync: every screen renders from cache
- [ ] The staleness warning appears, and no fabricated value is ever shown
- [ ] Web: the page opens with no connection after having been opened once
- [ ] Reconnecting and refreshing recovers cleanly

### Persistence
- [ ] A simulation survives force-stop and relaunch, and an update over the top
- [ ] Android: delete offers undo, and undo restores the simulation
- [ ] The simulation list shows the line saying where simulations are stored

### Presentation
- [ ] Light and dark themes, and a fresh install following the system setting
- [ ] Font scale at maximum: no clipped or overlapping text
- [ ] Landscape on a phone; a wide window on the web uses the rail and two
      columns
- [ ] Sharing each card produces readable text in WhatsApp and in mail, and
      contains only what the card shows
- [ ] Web on a laptop: the share button copies and says so; the install card
      asks for a bookmark
- [ ] The launcher icon shows all seven candles uncut under circular and
      squircle masks, and its themed variant still reads in one tone
- [ ] The "Máx" range scrubs smoothly across the whole series
- [ ] Both chart endpoints show a date and a value that match the series
- [ ] Typing a long amount groups it live, and the cursor stays where expected
- [ ] Every numeric field's *initial* value renders correctly before it is
      touched, not just after editing
- [ ] On a phone whose numeric keypad offers "." rather than ",", decimals still
      work in the rate fields
- [ ] A 25-year payment table scrolls smoothly in both axes
- [ ] CSV export opens correctly in a spreadsheet under a Chilean locale
- [ ] TalkBack, or a screen reader in the browser, reaches and announces every
      interactive control

### Devices
- [ ] Smallest supported: Android 8.0, a 5-inch screen
- [ ] Current: Android 15, a large screen
- [ ] Web: current Safari on an iPhone, Chrome on Android, and a desktop
      browser
