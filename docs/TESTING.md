# Testing

The app's value is entirely in its numbers, so the test suite is organised
around one principle: **anything that produces a number must be verifiable
without a browser**, and the figures a user could quote must be pinned so that
changing one is always deliberate.

```bash
cd web && npm test        # 133 tests, seconds
cd web && npx tsc --noEmit
python3 -m unittest discover -s tools -p "test_*.py"   # 17 more, no network
```

`.github/workflows/checks.yml` runs all three on every push and pull request.

The Python suite sits apart from the layer table below because it tests the
build tools rather than the app: `tools/sources.py`, which decides where the
bundled series come from. It reads the CMF's Chilean-formatted numbers against
the payload the Android app captured from the live API, finds the rows whatever
the response names them, and checks that a failing source hands its year to the
next one rather than losing it. None of it touches the network.

## Layers

| Layer | Suite | What it protects |
|---|---|---|
| Pure calculation | Vitest, 69 | Mortgage maths, due dates, UF conversions and windows, the restatement, date-lookup rules, sanity filtering |
| Pinned expectations | Vitest, 25 | The golden fixtures: mortgage, UF, bitcoin, converters; and the companion-indicator list |
| Formatting and input | Vitest, 10 | `es-CL` output and parsing |
| Data contracts and assets | Vitest, 15 | The bundled series, read from the shipped files |
| Navigation | Vitest, 7 | What a hash means, and what each screen's address is |
| Documentation | Vitest, 7 | The README and the docs against the code they describe |

The engines live in `domain/` with no DOM and no `fetch`, which is what makes
the first two layers possible at all. It is an architectural choice made for
testability, not an accident.

## The golden fixtures

Four files in `web/golden/` hold inputs and exact expected outputs at the
engines' own scale. Each is read by one suite, and every value must match as a
string, not within a tolerance.

| Fixture | Suite | Covers |
|---|---|---|
| `mortgage.json` | `goldenVectors.test.ts` | Both rate conventions, both prepayment modes, insurance and upfront costs, month-end clamping, the zero-rate divisor |
| `uf.json` | `ufGoldenVectors.test.ts` | Peso conversions, deltas, the restatement, the chart summary, formatting |
| `btc.json` | `btcGoldenVectors.test.ts` | Bitcoin conversions, the chart plan per horizon, the wording of a failed fetch |
| `converter.json` | `converterGoldenVectors.test.ts` | The three-field converters, where a rounding mistake shows as two answers on one screen |

**Nothing generates these files from the engines.** A fixture the engine wrote
would agree with whatever the engine currently does, which is no guarantee at
all. Changing a figure on purpose means editing the file by hand, and that is
the point: it is the moment worth pausing at. Every reported miscalculation
should arrive as a new case before it is fixed.

Two of the four exist because of specific real failures. The converter fixture
was written after one bitcoin read 72.476.915 pesos on load and 72.476.920
after an edit, because the peso figure was being derived from the
already-rounded dollar figure. The chart summary and the chart plan are in
their fixtures because they are design decisions — when a window is long
enough to annualise, what candle width a span uses — and a design decision
that changes while someone is editing something else is exactly what a fixture
is for.

## The documentation is tested too

The README is the front door, and a front door that describes a different
house is worse than no door at all. Prose cannot be tested, but the facts
around it can, and every one of these has drifted at least once:

- the version the README gives is the one `web/package.json` declares
- the test counts in the README and in this file are the real ones
- every relative link and every heading anchor across the README, `CLAUDE.md`
  and `docs/` resolves
- every screenshot kept in `docs/screenshots` is shown by the README, and every
  one it shows is kept
- every tab in the app's navigation is named somewhere in the README
- every test file is classified in the layer table above, and each row's count
  is the real one

`docs.test.ts` runs inside `npm test`, so a change that outdates the README
turns CI red in the commit that made it rather than a release later. When it
fails the fix is to update the document, never to relax the check. It found
its first bug immediately: the layer table above was missing the sanity-filter
suite, so it summed to 103 where the suite had 112.

The parts a test cannot read — whether the feature list still describes the
app, whether the screenshots still look like it — are covered by the rule in
[`CLAUDE.md`](../CLAUDE.md) and by the release checklist at the end of this
document. Screenshots are cheap to redo: `cd web && npm run screenshots`.

## What each suite asserts

### Mortgage engine, 21 tests

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

### UF engine, 20 tests

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

### Restatement, 9 tests

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

### Date lookup, 10 tests

Added after a real defect: asking for a date past the published horizon
answered with the last published day's value **and labelled it official**.

- A date beyond the horizon is never answered with another day's value; the
  day after the horizon is already unpublished, the horizon itself is valid.
- Today and past dates resolve exactly and are not marked as future.
- A substitute is offered only for gaps inside coverage, and is labelled.
- Dates before August 1977 are rejected; the first day of the series is not.
- Nothing cached and nothing nearby is "unavailable", never a wrong number,
  and an empty cache does not fabricate a horizon.

### Sanity filter, 9 tests

- **The real 2014 corruption is rejected**: 608,15 and 607,38 between two days
  worth about 24.627 are dropped, and the surrounding days survive.
- The largest genuine daily move in the series (0,2633 %) is kept.
- The allowance scales with the gap, so a multi-day jump is judged per day.
- An anchor lets the first value of a batch be checked too; without one it is
  trusted, which is the documented limit of the guard.
- Non-positive values never pass; input order does not matter.

### The bundled series, 15 tests

Read from the shipped files in `web/public/`, so a bad regeneration fails the
build rather than the browser.

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

### Formatting, 10 tests

`$40.880,36`, `22,23 UF`, `+3,25%`, `05-09-2026`, "hace 4 días". Parsing
accepts `40.880,36`, `$1.000`, `4,5` and `1.234 UF`; rejects `""`, `"abc"` and
`","`; formatting and parsing round trip. Counts get their separators too.
None of it comes from the browser's locale data: the separators and the month
names are the app's own, so no difference between browsers can change them.

### Navigation, 7 tests

Every screen has its own address, so a link to one can be sent, bookmarked or
opened in a tab. The pure half of that — what a hash means and what a screen's
hash is — is where the rules live, and they are rules about links that have
been out in the world:

- every screen's own hash resolves back to it, and no two screens share one
- no hash at all opens the overview
- the bitcoin screen is `btc` in the code and `#/bitcoin` in the address bar,
  and the internal name is deliberately not a route
- a hash mangled in transit still lands: a lost slash, a doubled one, a
  trailing one, the wrong case, or the tracking parameters chat apps and mail
  clients staple onto anything link-shaped
- **a route that no longer exists opens the overview, never nothing**, which is
  the same rule as everywhere else here: never a blank screen

### The companion indicators, 8 tests

Which figures are listed beside the three cards, in what order, and which are
asked for without being listed. A list of decisions rather than a calculation,
pinned for the same reason the chart plan is: it changes by hand, and a hand
can change it while meaning to change something else.

- the dólar observado is not listed, because it has a card of its own — and is
  still fetched, because three screens convert through it
- everything listed is fetched
- a series the source has abandoned (`dolar_intercambio`, frozen since 2014) or
  that the app already has live (`bitcoin`) is never listed
- the order is the one that was decided, spelled out in full
- a monthly figure is dated to its month and a daily one to its day, and an
  indicator the app has never heard of is treated as daily

## Bugs the suites found

Worth recording, because each would have shipped otherwise.

1. **A future date was answered with another day's value, labelled official.**
   The fix is a closed set of outcomes plus a bounded calendar, and the
   regression is pinned by its own test.
2. **The public data source was serving corrupt values.** Preloading the whole
   series surfaced a 97 % single-day collapse in December 2014. Both the
   generator and the running app now reject implausible movements, and the
   seed tests pin the specific values.
3. **A converter disagreed with itself.** One bitcoin read a different peso
   figure on load than after an edit, because the pesos were being derived
   from the already-rounded dollars. The converter fixture pins every field
   against the engine.
4. **A chart's range chips went off screen when the history expanded.** The
   chips sat below a chart that grows to 200 px, which pushed them past the
   fold. They now sit above it.
5. **Removing a figure from a list stopped it being fetched.** The dólar
   observado was dropped from the companion list, correctly, since the overview
   gives it a card of its own — but the list was also the request, so three
   screens quietly lost the rate they convert through. The bitcoin card lost
   its peso figure, the bitcoin screen lost its peso and UF readings, and the
   UF converter lost its dollar field. Nothing threw and nothing was wrong on
   screen; the figures were simply absent. What is fetched and what is shown
   are now two lists, and the suite above was written before the fix.

## The one that escaped: a postmortem

An earlier version of the inflation screen rendered its amount as **`4..000`**.
It shipped, and a user found it. The screen was the Android app's, and that
channel is gone, but every lesson in it is about this repository rather than
that platform.

**What happened.** Numeric fields hold raw text and the display adds the
grouping. That screen's default was still the pre-formatted `"4.000"`, so the
transformation counted the dot as a digit position and inserted a second one.

**Why nothing caught it.** Three separate gaps, each a general lesson:

1. *The transformation's tests only ever fed it valid input.* The one state
   that could break it, one containing a grouping character, was never tried,
   and the function produced garbage silently instead of coping or failing.
2. *The invariant was a convention, not a check.* Several defaults were changed
   by hand when grouping was introduced and one, in a different screen, was
   missed. Nothing asserted that defaults are raw.
3. *The UI test asserted the results, not the field.* The computation stayed
   correct the whole time, because the parser discards dots, so the app
   produced right answers behind a corrupted display and no assertion could
   fail.

There was also a process failure: the change was verified by screenshotting
the screen where the defaults had been fixed, rather than the one that had not
been touched.

**Where this app stands.** Its numeric fields group on blur rather than on
every keystroke, so there is no cursor mapping to get wrong, and
`groupForDisplay` leaves an already-grouped string alone. Its defaults are raw
(`"4000"`, not `"4.000"`). But that last one is still a convention rather than
a check: nothing asserts it, which is lesson 2 above, unlearned. A regression
test that does not fail on the original bug is decoration, and a test that does
not exist is worse.

## Manual QA checklist

Automated coverage stops at the browser boundary. These are checked by hand
before a release.

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
- [ ] The page opens with no connection after having been opened once, and
      every chart and date lookup works from the bundled series
- [ ] The staleness warning appears, and no fabricated value is ever shown
- [ ] Reconnecting and refreshing recovers cleanly
- [ ] A deploy while a page is open does not leave it asking for a bundle that
      no longer exists

### Persistence
- [ ] A simulation survives a reload and a browser restart
- [ ] The simulation list shows the line saying where simulations are stored

### Presentation
- [ ] Light and dark themes, and a fresh visit following the system setting
- [ ] Text scaled up: no clipped or overlapping text
- [ ] A narrow window uses the bottom bar; a wide one uses the rail and two
      columns
- [ ] Sharing each card produces readable text in WhatsApp and in mail, and
      contains only what the card shows
- [ ] On a laptop the share button copies and says so; the install card asks
      for a bookmark
- [ ] Added to a phone's home screen, the app opens standalone with the right
      icon
- [ ] The "Máx" range scrubs smoothly across the whole series
- [ ] Both chart endpoints show a date and a value that match the series
- [ ] Typing a long amount groups it when the field loses focus
- [ ] Every numeric field's *initial* value renders correctly before it is
      touched, not just after editing
- [ ] On a phone whose numeric keypad offers "." rather than ",", decimals still
      work in the rate fields
- [ ] A 25-year payment table scrolls smoothly in both axes
- [ ] CSV export opens correctly in a spreadsheet under a Chilean locale
- [ ] A screen reader reaches and announces every interactive control

### Browsers
- [ ] Current Safari on an iPhone, Chrome on Android, and a desktop browser
