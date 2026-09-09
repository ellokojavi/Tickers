# Requirements

What the app is held to, on both channels unless a row says otherwise. The
tests that enforce most of these are listed in [Testing](TESTING.md).

## Functional

### UF

| ID | Requirement |
|----|-------------|
| RF-1 | Display today's official UF value with its publication date |
| RF-2 | Display the change since the previous day and the 30-day percentage change |
| RF-3 | Convert UF ⇄ CLP ⇄ USD, deriving every field from the one typed rather than from another field's rounded display |
| RF-4 | Display the companion indicators (IVP, USD, EUR, UTM, CPI) |
| RF-5 | Query the UF value for any date within coverage |
| RF-5b | Present today's value and the historical series as one destination, with the history always visible |
| RF-5c | Refuse to answer for a date with no published value, and make such dates unselectable |
| RF-6 | Display already-published future UF values, explicitly marked as official |
| RF-7 | Never extrapolate or project a value that has not been published |
| RF-8 | Chart the series over selectable ranges with touch scrubbing, without ever altering the headline value |
| RF-8b | Report the range's change both cumulatively and, when the window is long enough, annualised |
| RF-8c | Label both ends of the chart with their date and value |
| RF-8d | Keep the day-by-day list collapsed until requested |
| RF-8e | End the chart on today, never on a published future day |

### Dólar observado

| ID | Requirement |
|----|-------------|
| RF-24 | Display the latest observed dollar with its publication date and the change since the previous publication |
| RF-25 | Chart the series since January 1984 with the same ranges, scrubbing and labels as the UF |
| RF-26 | Convert USD ⇄ CLP at the published rate |
| RF-27 | Show no value for a weekend or holiday: carry the last published value forward and say which day it is from |

### Bitcoin

| ID | Requirement |
|----|-------------|
| RF-28 | Display the spot price in dollars, converted to pesos with the observed dollar and expressed in UF |
| RF-29 | Name the source that answered and the date of the dollar used |
| RF-30 | Try several independent price sources so one exchange's outage is not the app's |
| RF-31 | Chart horizons from one hour to five years, each at its own candle width |
| RF-32 | Convert BTC ⇄ USD ⇄ CLP, with the peso field carrying the dollar's date |
| RF-33 | Say when a price is stale and why a fetch failed, in words a user can act on |

### Inflation

| ID | Requirement |
|----|-------------|
| RF-9 | Restate an amount between any two dates, at day precision, through the UF |
| RF-10 | Show the UF on both dates and the amount in UF units |
| RF-11 | Report the adjustment factor, accumulated variation and annualised rate |
| RF-12 | Make out-of-coverage dates unselectable, and explain the coverage |

### Mortgage simulator

| ID | Requirement |
|----|-------------|
| RF-13 | Simulate a `UF + x%` mortgage and produce a full amortisation schedule |
| RF-13b | Let the user set the first instalment's due date and derive every later date from it |
| RF-14 | Support both annual-to-monthly rate conventions used in Chile |
| RF-15 | Model life and property insurance, fees, stamp tax and upfront costs |
| RF-16 | Compute the CAE from actual cash flows |
| RF-17 | Model prepayments, reducing either term or payment |
| RF-18 | Create, read, update, duplicate and delete saved simulations |
| RF-18b | Open a saved simulation on its result; open a new one on its form |
| RF-19 | Persist simulations across restarts and updates, on the device only |
| RF-19b | Tell the user, on the screen itself, that simulations stay on the device and are never sent anywhere |
| RF-20 | Export a schedule to CSV and share it |

### Sharing and data

| ID | Requirement |
|----|-------------|
| RF-20b | Share any card as preformatted text, containing what the card shows and nothing more |
| RF-20c | On a phone or a tablet share through the share sheet; on a laptop or a desktop copy to the clipboard, and say which |
| RF-21 | Refresh data in the background (Android) or on opening (web), and on demand |
| RF-22 | Operate fully offline from the bundled series |
| RF-22b | Ship the complete daily series and refresh only what is genuinely new |
| RF-22c | Reject implausible values from any source rather than storing them |
| RF-23 | Label the origin of every value and whether the source is official |
| RF-34 | Web only: offer to install on the home screen on a phone or a tablet, and to bookmark on a computer; remember a dismissal |

## Non-functional

| ID | Requirement |
|----|-------------|
| RNF-1 | Android 8.0+ (minSdk 26), targetSdk 35; any current browser for the web app |
| RNF-2 | Spanish (Chile) throughout; `es-CL` number formatting (`$40.880,36`) on input as well as output, and on **every** figure, counts included |
| RNF-3 | Functional with no network connection; never a blank screen |
| RNF-4 | APK under 15 MB; the minified release build is about 1,8 MB. The web bundle is under 100 kB gzipped |
| RNF-5 | Cold start under 1,5 s |
| RNF-6 | No analytics, no account, no personal data. No runtime permission prompts on Android: `INTERNET` and `ACCESS_NETWORK_STATE`, plus the normal-level permissions WorkManager merges in |
| RNF-7 | All monetary arithmetic in exact decimals (`BigDecimal`, `big.js`); a float never holds money |
| RNF-8 | WCAG AA contrast, font-scaling support, screen-reader labels on every control |
| RNF-9 | Light and dark themes, following the system until set manually |
| RNF-10 | Calculation engines are pure code with no platform imports, testable without a device or a browser |
| RNF-11 | The two channels produce identical numbers, enforced by shared golden vectors run in CI on every commit |
| RNF-12 | State independence from the CMF, the Banco Central, the INE and every exchange, and attribute sources as their terms require, without interrupting the user |
| RNF-13 | One mark, a candlestick chart, generated for every icon on both channels from a single description |
