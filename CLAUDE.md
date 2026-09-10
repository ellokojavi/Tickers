# Working in this repository

Tickers ships twice: an Android app in Kotlin and a web app in TypeScript, one
product with one set of numbers. These are the rules that keep it that way.
The reference documentation is in [`docs/`](docs/); this file is the working
agreement.

## The README is the front door, and it is never out of date

**Any change that makes the README less true is not finished until the README
is true again.** The README is how anyone meets this project. A stale one is
worse than a short one: it teaches something false with the project's own
authority.

In the same commit as the change, not in a follow-up:

| If you change | Update |
|---|---|
| A feature, a screen, or a tab | The feature list, in **both** languages, and the screenshots |
| The version | `app/build.gradle.kts`, `web/package.json` and the README's status |
| How either channel is built, tested or run | The Building section |
| The number of tests | The counts in the README, in `docs/TESTING.md` and below |
| What the app looks like | The screenshots: `cd web && npm run screenshots` |
| A data source, or what is stored | The user-facing promises, and `docs/DATA.md` |
| Anything the deeper docs describe | That document, from the same commit |

Both language sections say the same things. Spanish is the users' language and
comes first; English is not an afterthought that lags a release behind.

`web/src/__tests__/docs.test.ts` enforces what can be enforced: the version,
the test counts, every relative link and anchor, the screenshots, the tabs and
the layer table in `docs/TESTING.md`. It runs in `npm test`, which CI runs on
every push and pull request, so a commit that outdates the README turns the
build red. **When it fails, update the document. Never relax the check.** Prose
it cannot read is on you.

## The two channels are one product

- **The numbers may never differ.** Anything a user could quote, screenshot or
  act on is identical on both: a UF value, a conversion, an equivalence, a
  dividend, a CAE. Enforced by the fixtures in `shared/golden/`, which both
  suites read and must match exactly, as strings.
- **Land both channels in the same commit.** Not "web now, Android later":
  later is how they came apart the first time. If one genuinely cannot have a
  feature, say so in the commit message and add a row to the table in
  `shared/PARITY.md`.
- **Copy the words, not just the behaviour.** A field called "Pesos (al 8 de
  septiembre)" on one channel and "Pesos" on the other is drift, and it is the
  kind users notice first.
- Anything that computes a number lives in `domain/`, on both sides, and gets a
  golden case. Anything that only draws is UI and is written twice, natively.

Read [`shared/PARITY.md`](shared/PARITY.md) before adding a feature.

## The product's own rules

- **Never invent a value.** A day with no published UF is refused, not
  extrapolated. A weekend has no dollar. A chart ends today even when the
  series runs past it. A lookup never answers with a neighbouring day's value.
- **Money is exact.** `BigDecimal` on Android, `big.js` on the web. A float
  never holds a peso. Round once, where a figure is shown, never on an
  intermediate another figure comes from.
- **Every figure carries its source and its date**, and whether that source is
  official.
- **A card shares itself, and only itself.** A share button sends what is on
  the card, nothing from the cards around it.
- **Nothing leaves the device**, and the screens that hold personal figures say
  so.
- Every number a user sees is formatted `es-CL`, counts included.

## Before you commit

```bash
cd web && npm test && npx tsc --noEmit    # 119 tests, includes the docs check
./gradlew test                            # 157 JVM tests
```

The 13 instrumented tests (`./gradlew connectedAndroidTest`) need a device and
are run before a release, along with the manual checklist in
`docs/TESTING.md`.

Commit messages are an imperative sentence saying what changed for the user,
with ", on both channels" when it lands on both, and a body explaining why
rather than what. The diff already says what.

## Generated files are never edited by hand

| Output | Source |
|---|---|
| Every app icon, both channels | `tools/build_icons.py` |
| `app/src/main/assets/uf_daily.txt` | `tools/build_uf_daily_seed.py` |
| `app/src/main/assets/usd_daily.txt` | `tools/build_usd_daily_seed.py` |
| `web/public/*.txt` | copied from the Android assets by `web/scripts/sync-data.mjs` |
| `docs/screenshots/*.png` | `web/scripts/screenshots.mjs`, as `npm run screenshots` |

Edit the script and re-run it. The bundled series additionally have to pass the
seed tests before they land, which is what the daily refresh workflow does.
