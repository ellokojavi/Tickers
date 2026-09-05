# Testing strategy

The app's value is entirely in its numbers, so the test suite is organised
around one principle: **anything that produces a number must be verifiable
without a device, and anything a user can tap must be verified on one.**

```bash
./gradlew test                  # 107 JVM tests  — seconds, no device
./gradlew connectedAndroidTest  # 10 UI tests    — needs a device or emulator
```

---

## Layers

| Layer | Runner | Count | What it protects |
|-------|--------|-------|------------------|
| Pure calculation | JUnit | 71 | Mortgage maths, due dates, inflation index, UF conversions, date-lookup rules |
| Formatting & parsing | JUnit | 8 | `es-CL` output and input round trips |
| API contracts | JUnit | 5 | Both providers' payload shapes |
| Persistence & assets | Robolectric | 16 | Room schema, type converters, bundled dataset |
| UI flows | Instrumented | 10 | Navigation, offline rendering, live computation, screen ordering |

The engines live in `domain/` with **no Android imports**, which is what makes
the first three layers possible at all. This is an architectural choice made
for testability, not an accident.

---

## What each suite asserts

### `MortgageEngineTest` — 21 tests

The amortisation engine is the highest-risk code in the app: it is the only
place where a silent error produces a plausible-looking wrong answer.

- The `BigDecimal` payment implementation is checked against the closed-form
  analytic formula computed independently in `Double`.
- The schedule fully amortises: the final closing balance is 0.
- Principal payments sum exactly to the loan amount.
- The balance chain is internally consistent: every row's closing balance
  equals its opening balance minus principal minus prepayment, and equals the
  next row's opening balance.
- Interest equals opening balance × monthly rate, row by row.
- Insurance is charged on top of the dividend, and the *desgravamen* shrinks
  as the balance does.
- Both rate conventions produce the expected monthly rates, and the effective
  conversion is always the cheaper one.
- A zero rate splits the principal evenly and charges no interest.
- A term-reducing prepayment shortens the schedule and lowers total interest.
- A payment-reducing prepayment keeps the term and lowers later dividends.
- A prepayment larger than the balance is capped instead of overshooting.
- **CAE with no fees converges on the effective annual rate** — an independent
  check that the bisection solver is correct.
- CAE rises once fees and insurance are added.
- Upfront costs are computed off the loan amount, not the property value.
- A longer term lowers the payment and raises total interest.

On due dates:

- Instalment 1 falls exactly on the date the user chose.
- Instalments advance one month at a time, checked at the 2nd, 12th, 13th and
  final rows.
- A 31st clamps to the last day of shorter months and recovers afterwards
  (31 Jan → 28 Feb → 31 Mar → 30 Apr), matching how a lender schedules it.
- **Changing the due date moves no money**: payment, total cost and CAE are
  identical across two very different start dates.

### `InflationEngineTest` — 11 tests

- **The CPI derivation is validated against reality**: the monthly variation
  implied by the UF is compared with the CPI the INE actually published for
  every month of 2024. Maximum deviation: 0.000 pp.
- The December 2008 deflation (−1.2%, Chile's sharpest) is present and correct.
- The bundled seed covers August 1977 to the present with **no missing month**
  inside the span — a gap would silently corrupt every conversion across it.
- No month-over-month step is implausible (the bound allows real deflation but
  catches a truncated or misparsed dataset).
- Converting forward and then back returns the original amount.
- A same-month conversion is a no-op with factor 1.
- 1990 → 2024 lands in the expected 6×–8× range with a sane annualised rate.
- The annualised rate, compounded over the period, reproduces the cumulative
  factor.
- **Out-of-coverage months throw instead of returning a guess.**
- `supports()` reports the true coverage window at both edges.

The seed test reads `app/src/main/assets/uf_monthly_seed.json` directly — the
same file that ships in the APK — so regenerating it badly fails the build
rather than the phone.

### `UfEngineTest` — 17 tests

Centred on the app's defining subtlety: **the series legitimately contains
future dates.**

- "Current" ignores published future dates and returns today's value.
- "Future" returns exactly the days beyond today, in order.
- Conversions round trip; a zero rate does not divide by zero.
- Deltas carry the correct sign.
- An empty series returns null rather than crashing.
- Annualisation: a full year returns its own change, six months compound
  correctly, declines stay negative, short windows magnify as the arithmetic
  requires, and impossible inputs return zero instead of NaN.
- Downsampling keeps the endpoints and the requested size, preserves the
  overall movement, leaves short series untouched and survives absurd budgets.

### `UfSanityTest` — 9 tests

The public feed serves corrupt values, so nothing is stored without a check.

- **The real 2014 corruption is rejected**: 608,15 and 607,38 between two days
  worth about 24.627 are dropped, and the surrounding days survive.
- The largest genuine daily move in 49 years (0,2633%) is kept.
- The allowance scales with the gap, so a multi-day jump is judged per day.
- An anchor from the database lets the first value of a batch be checked too;
  without one it is trusted, which is the documented limit of the guard.
- Non-positive values never pass; input order does not matter.

### `UfDailySeedParseTest` — 9 tests

The bundled-series format, parsed without Android.

- Centavos become exact two-decimal pesos with no float step.
- A blank line is a gap and must still advance the calendar, so later dates do
  not shift.
- A missing or unparseable header yields nothing rather than wrong dates.
- Junk lines are ignored, never guessed at.
- **The shipped asset is checked directly**: every value is positive, no
  day-over-day jump exceeds the plausibility bound, it survives the runtime
  filter untouched, and it has **no missing day at all** — so a bad
  regeneration fails the build, not the phone.
- The two days the source corrupts in December 2014 carry their real value of
  24.627,10, recovered from the re-adjustment period they sit in.

### `UfLookupTest` — 11 tests

Added after a real defect: asking for a date past the published horizon
answered with the last published day's value **and labelled it official**. Two
faults compounded — the lookup fell back to "nearest earlier" in the future
direction, and the UI checked "is future" before "is not exact".

The rules now live in a pure resolver with a closed set of outcomes:

- A date beyond the horizon is never answered with another day's value.
- The day after the horizon is already unpublished; the horizon itself is a
  valid published future value.
- Today and past dates resolve exactly and are not marked as future.
- A substitute is offered only for gaps **inside** coverage, and is labelled.
- Dates before August 1977 are rejected; the first day of the series is not.
- Nothing cached and nothing nearby is "unavailable", never a wrong number.
- An empty cache does not fabricate a horizon.

The calendar is bounded by the same horizon, so an unpublished day cannot be
picked in the first place — the resolver is the second line of defence.

### `FormatTest` — 8 tests

`$40.880,36`, `22,23 UF`, `+3,25%`, `05-09-2026`, "hace 4 días". Parsing accepts
`40.880,36`, `$1.000`, `4,5` and `1.234 UF`; rejects `""`, `"abc"` and `","`.
Formatting and parsing round trip.

### `DtoParsingTest` — 5 tests

Contract tests against **captured real payloads** from both providers.

- CMF's Chilean-formatted strings (`"40.880,36"`) parse to exact decimals.
- mindicador's floats keep exact cents — `BigDecimal.valueOf` must not
  introduce binary float noise.
- Unknown fields and missing sections degrade gracefully.
- Empty payloads yield no values rather than an exception.

If either API changes shape, this fails here instead of silently on a phone.

### `DatabaseCrudTest` and `UfDaoTest` — 12 tests (Robolectric)

The real Room schema on the JVM.

- A saved simulation round trips with **no loss of decimal precision** — the
  direct test of the "money is TEXT, never REAL" rule.
- Prepayments survive their JSON round trip, including the mode enum.
- Update mutates the row instead of inserting a second one, preserves
  `createdAt`, and advances `updatedAt`.
- Duplicate produces a new id with identical inputs.
- Delete removes only its target.
- Duplicating a missing simulation returns null.
- The list orders by most recently updated.
- Upsert replaces rather than duplicates a date.
- `atOrBefore` resolves backwards only, never forwards.
- `monthAnchors` returns only the 9th of each month.
- `maxDate` sees published future days.

### `UfSeedTest` — 2 tests (Robolectric)

Loads the seed through the **real asset pipeline**, catching a renamed file, an
asset excluded from packaging, or malformed JSON. Also verifies the in-memory
cache returns the same instance.

### `NavigationSmokeTest` — 10 tests (instrumented)

Runs on a device or emulator against the real activity.

- The app launches showing today's value and the converter.
- All three tabs are present.
- The history and its range selector are visible with no interaction at all.
- The date lookup is present on arrival.
- **The day-by-day list starts collapsed** and expands on demand, which is what
  keeps a screen carrying hundreds of rows usable.
- **Inflation computes from bundled data with no network** — the direct test of
  the offline-first requirement.
- Credits opens the editor and produces a result from defaults alone,
  including a CAE.
- The theme toggle does not break the screen.
- **A saved simulation opens on its result, a new one on its form** — the
  ordering rule, asserted in both directions. The test names its simulation
  uniquely and deletes it afterwards, so the suite is idempotent.
- The payment table's first row carries the chosen due date.

Assertions deliberately target chrome and bundled-data content, never a value
that depends on a successful network call, so the suite is not flaky on a
machine with poor connectivity.

---

## Five bugs this suite already caught

Worth recording, because both would have shipped otherwise:

**1. WorkManager crashed the app at startup under test.** `Application.onCreate`
called `WorkManager.getInstance()`, which throws when the startup initializer
has not run. Fixed by switching to on-demand initialisation (`Configuration.Provider`
plus removing the default initializer from the manifest) and wrapping the
scheduling in `runCatching`. Background sync is a convenience and must never be
able to prevent the app from starting.

**2. The "new simulation" button was invisible to screen readers.** Its label
sits in a slot that is not merged into the node's semantics, so TalkBack
announced only "Button". Found because the UI test could not locate it either.
Fixed with an explicit `contentDescription`.

**3. A future date was answered with another day's value, labelled official.**
See `UfLookupTest` above. The fix is a closed set of outcomes plus a bounded
calendar, and the regression is pinned by its own test.

**4. The public data source was serving corrupt values.** Preloading the whole
series surfaced a 97% single-day collapse in December 2014: the feed returns
608,15 where the UF was about 24.627. It had been invisible while the app only
fetched recent years. Both the generator and the running app now reject
implausible movements, and two tests pin the specific values.

**5. Expanding the history put its own controls off screen.** The range chips
sat below a chart that grows to 200dp, which pushed them past the fold on a
1080×2400 device: tapping "Ver histórico" revealed a taller chart and no way to
change its range without scrolling. The test failed on `assertIsDisplayed`,
which is precisely the distinction that matters — the node existed, it just was
not visible. The chips now sit above the chart, where the control that governs
a view belongs.

---

## Manual QA checklist

Automated coverage stops at the device boundary. These are checked by hand
before a release.

### Data correctness
- [ ] Today's UF matches [CMF](https://www.cmfchile.cl) to the cent
- [ ] Future values extend to the 9th of next month and are labelled as
      official, never as projections
- [ ] A date before Aug 1977 reports missing coverage instead of a number
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
- [ ] Reconnecting and pulling to refresh recovers cleanly

### Persistence
- [ ] A simulation survives force-stop and relaunch
- [ ] A simulation survives an app update (install over the top)
- [ ] Delete offers undo, and undo restores the simulation

### Presentation
- [ ] Light and dark themes, and following the system setting
- [ ] Font scale at maximum: no clipped or overlapping text
- [ ] Landscape orientation
- [ ] Expanding the history keeps its range selector and its change figures on screen
- [ ] Sharing today's card produces readable text in WhatsApp and in mail
- [ ] The launcher icon fills a circular mask edge to edge, stays a circle under
      a squircle mask, and its themed variant still reads in one tone
- [ ] The "Máx" range scrubs smoothly across all 49 years
- [ ] Both chart endpoints show a date and a value that match the series
- [ ] A 25-year payment table scrolls smoothly in both axes
- [ ] CSV export opens correctly in a spreadsheet under a Chilean locale
- [ ] TalkBack reaches and announces every interactive control

### Devices
- [ ] Smallest supported: Android 8.0, ~5" screen
- [ ] Current: Android 15, large screen

---

## Regenerating the bundled dataset

```bash
python3 tools/build_uf_daily_seed.py
./gradlew test    # the seed tests must pass before the change is committed
```

The generator refuses to write a truncated series and prints every value it
rejects. `UfDailySeedParseTest` and `InflationEngineTest` then read the
generated file directly, so a truncated, corrupted or partially-fetched
regeneration fails immediately.
