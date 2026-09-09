# Calculations

Everything in Tickers that produces a number, and why it is computed the way it
is. Each of these exists twice, in Kotlin under `app/.../domain/engine/` and in
TypeScript under `web/src/domain/`, and both implementations must agree with
the fixtures in `shared/golden/` exactly. See [Parity](../shared/PARITY.md).

## Money

All monetary arithmetic is exact decimal: `BigDecimal` on Android, `big.js` on
the web. A floating-point number never holds a peso. The UF is carried at the
two decimals it is published with, pesos are whole (Chile has not used centavos
for decades), dollars and UF amounts are shown to two decimals, and UF units in
the inflation calculator to four. Rounding happens once, at the point a figure
is shown, never on an intermediate value that another figure is derived from.

## Restating an amount through the UF

The inflation calculator restates an amount between **two dates**, not two
months. The amount is converted into UF on the origin date and back into pesos
on the target date:

```
adjusted = amount × UF(to) / UF(from)
```

This is the mechanism Chilean contracts, rents and debts are actually
re-adjusted with, and because the UF is published every calendar day, the
answer is exact to the day. The screen reports the UF on both dates, the amount
in UF units, the adjustment factor, the accumulated variation and the
equivalent annual rate, which is the factor compounded back over the elapsed
days.

**Why dates rather than months.** An earlier version asked for months and
showed two readings side by side: one through a monthly CPI index and one
through UF units. They answered subtly different questions and differed by a
few percent, because the UF carries the CPI with a lag by construction. The
reason for the monthly granularity was the CPI itself: the INE publishes one
index per month, so there is no such thing as the price level on a given day.
The UF, by contrast, exists every day. Asking for dates makes the question well
posed and leaves exactly one answer.

The consequence is that the figure is the UF re-adjustment, which tracks
inflation with the UF's own lag rather than reproducing the INE's
month-on-month series. The screen says so.

### The price index the UF encodes

Chile's CPI is published as a **monthly percentage with one decimal**. Chaining
those figures from 1990 to today means multiplying some 430 rounded numbers,
and the rounding error compounds. The UF is a better index. It is re-adjusted
daily so that:

```
UF(9th of month M+1) / UF(9th of month M) = 1 + CPI variation of month M−1
```

and it is published to two decimals on a value in the tens of thousands, about
`1e-7` relative precision, several orders of magnitude better than a
one-decimal percentage. So the monthly price index is simply the UF on the 9th
of the month two months later.

**This is verified, not assumed.** The seed tests on both channels reproduce
every monthly CPI figure the INE published for 2024 from the bundled UF series,
within the published figure's own single decimal, and check that the December
2008 deflation of −1,2 % survives in it. The calculator no longer needs this
mapping, since it converts between dates through the UF directly, but the
relationship is what makes the series trustworthy as an inflation measure, so
it stays under test.

## Looking up a date

The UF series legitimately contains future dates: the value is published about
a month ahead, on the 9th of each month for the period to the 9th of the next.
That creates three rules the lookup enforces, in a pure resolver with a closed
set of outcomes:

- **"Today" ignores the future.** The headline value is the value for today,
  never the last published day.
- **A date beyond the published horizon is never answered with another day's
  value.** The app says the value does not exist yet, and the calendar will not
  let the day be picked in the first place.
- **A substitute is offered only for gaps inside coverage**, is always the
  nearest earlier published day, and is labelled as such.

The rules were written after a real defect: asking for a date past the horizon
once answered with the last published value and labelled it official.

The dollar has the mirror-image rule. It is published on business days only, so
a weekend or holiday has no value and none is invented: screens carry the last
published value forward and say which day it is from.

## Charts

Every history chart draws the same way on both channels. The window for a
range ends today, never on a published future day, because a historical chart
is a record of what has happened and the days already published beyond it have
a card of their own. The window is thinned to at most 400 points before drawing,
since a phone cannot resolve more and rebuilding an 18.000-segment path on
every pointer event would make scrubbing crawl. Downsampling keeps both
endpoints and the overall movement.

The summary above the line reports the change over the window and, when the
window is long enough for it to mean anything, the annualised rate. Whether a
window is long enough is a design decision, so it is pinned in the golden
vectors: the two channels disagreeing about it would show as one chart saying
more than the other.

Scrubbing never changes the headline. The explored day is reported separately,
so a screen cannot misstate what the UF is worth today.

## Bitcoin

Bitcoin is priced in dollars, as the market does, and converted to pesos with
the Banco Central's observed rate:

```
clp = usd × dólar observado
uf  = clp / UF(today)
```

The peso figure therefore carries the dollar's publication date, which the
screen shows, rather than implying it is as live as the price. The UF figure
is the point: it is the only reading of bitcoin that nets out Chilean
inflation, and it is what makes a five-year chart comparable across its own
span.

Chart horizons run from one hour to five years. Each horizon has a candle
width, chosen so the chart reads well and costs one request, and that plan is
in the golden vectors because two channels drawing the same span at different
resolutions would be a difference nobody would think to look for.

## Converters

The three-field converters (UF, pesos, dollars; and bitcoin, dollars, pesos)
derive both other fields from the one that was typed, through the engine,
never from each other's already-rounded display. The distinction matters: one
bitcoin once read 72.476.915 pesos on load and 72.476.920 after an edit,
because the peso figure was being derived from the rounded dollar figure. The
converter fixtures in `shared/golden/converter.json` were written after that.

## Mortgage model

Payments follow the French system, a constant capital-plus-interest amount:

```
payment = P · i / (1 − (1 + i)^(−n))
```

Insurance is charged **on top of** that payment and is deliberately not
constant: the *desgravamen* premium tracks the outstanding balance, so the real
monthly outflow falls over the life of the loan. The app shows both figures.

The schedule carries real dates. The user chooses the due date of the first
instalment; later instalments fall on the same day each month, clamped to the
last day of shorter months exactly as a lender does (31 January, 28 February,
31 March, 30 April). Changing the due date moves no money: payment, total cost
and CAE are identical across any two start dates.

Cost inputs are *desgravamen* (a monthly percentage of the outstanding
balance), *incendio y sismo* (a fixed monthly UF amount), the origination fee,
stamp tax (*impuesto de timbres*, computed on the loan amount, not the property
value) and notary, appraisal and registry costs. Prepayments (*abonos a
capital*) either shorten the term or lower the payment, and one larger than the
balance is capped rather than overshooting.

### The rate convention caveat

Chilean lenders quote an annual rate but do not all convert it to a monthly
rate the same way:

- `monthly = annual / 12`, the common convention and the app's default
- `monthly = (1 + annual)^(1/12) − 1`, the effective equivalent, always slightly
  cheaper

Rather than pick one and silently mismatch a real bank quote, **both are
selectable**. If a simulation does not match a lender's own figure, switching
the convention is the first thing to try. That the default has not yet been
checked against a published bank quote is the one reason the app is versioned
below 1.0.

### CAE

The *Carga Anual Equivalente* is solved by bisection: the app finds the monthly
rate at which the present value of every payment the borrower makes (dividend,
insurance, prepayments) equals the loan amount net of upfront costs, then
annualises it. With no fees and no insurance it converges on the effective
annual rate, which both test suites assert as an independent check on the
solver.
