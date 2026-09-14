# Working in this repository

Tickers is a web app in TypeScript: one product, one set of numbers, served as
a static site. These are the rules that keep it honest. The reference
documentation is in [`docs/`](docs/); this file is the working agreement.

It shipped as an Android app in Kotlin as well until September 2026. That
channel is gone, and the tag `android-final` is the last commit that had it.
Nothing here should be written as though it still exists.

## The README is the front door, and it is never out of date

**Any change that makes the README less true is not finished until the README
is true again.** The README is how anyone meets this project. A stale one is
worse than a short one: it teaches something false with the project's own
authority.

In the same commit as the change, not in a follow-up:

| If you change | Update |
|---|---|
| A feature, a screen, or a tab | The feature list, in **both** languages, and the screenshots |
| The version | `web/package.json` and the README's status |
| How the app is built, tested or run | The Building section |
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

## Where the numbers live

- **Anything that computes a number lives in `domain/`**, with no DOM and no
  `fetch` in it. That is what makes it testable without a browser, and it is an
  architectural choice made for testability rather than an accident.
- **Anything a user could quote, screenshot or act on gets a golden case** in
  `web/golden/`: a UF value, a conversion, an equivalence, a dividend, a CAE, a
  candle width. The fixtures hold inputs and exact expected outputs as strings,
  not within a tolerance, and they are never generated from the engine — a
  fixture the engine wrote agrees with whatever the engine currently does.
- **Changing a figure on purpose means editing the fixture deliberately.**
  That is the point: it is the moment worth pausing at.
- Every reported miscalculation arrives as a new case **before** it is fixed.
- Anything that only draws is UI, and does no arithmetic of its own.

## The product's own rules

- **Never invent a value.** A day with no published UF is refused, not
  extrapolated. A weekend has no dollar. A chart ends today even when the
  series runs past it. A lookup never answers with a neighbouring day's value.
- **Money is exact.** `big.js` everywhere; a `number` never holds a peso. Round
  once, where a figure is shown, never on an intermediate another figure comes
  from.
- **Every figure carries its source and its date**, and whether that source is
  official.
- **A card shares itself, and only itself.** A share button sends what is on
  the card, nothing from the cards around it.
- **Nothing leaves the device**, and the screens that hold personal figures say
  so.
- Every number a user sees is formatted `es-CL`, counts included.
- **Every history chart goes through `ui/HistoryCard.tsx`** and reads its
  summary from `chartSummary`. A chart that needs something the card lacks
  gets the card extended, never a local exception.

## Before you commit

```bash
cd web && npm test && npx tsc --noEmit    # 133 tests, includes the docs check
python3 -m unittest discover -s tools -p "test_*.py"   # the source chain
```

The manual checklist in `docs/TESTING.md` is worked through before a release.

Commit messages are an imperative sentence saying what changed for the user,
with a body explaining why rather than what. The diff already says what.

## Generated files are never edited by hand

| Output | Source |
|---|---|
| Every app icon | `tools/build_icons.py` |
| `web/public/uf_daily.txt` | `tools/build_uf_daily_seed.py` |
| `web/public/usd_daily.txt` | `tools/build_usd_daily_seed.py` |
| `docs/screenshots/*.png` | `web/scripts/screenshots.mjs`, as `npm run screenshots` |

Edit the script and re-run it. The bundled series additionally have to pass the
seed tests before they land, which is what the daily refresh workflow does.

**Where a series comes from is decided in one place**, `tools/sources.py`: the
CMF when `CMF_API_KEY` is in the environment, mindicador.cl when it is not. Add
a source there, never in a generator. The reasoning, the measured evidence for
every candidate and what is still unverified are in
[`docs/DATA.md`](docs/DATA.md).
