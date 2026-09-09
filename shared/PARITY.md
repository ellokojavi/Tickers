# Keeping the two channels the same app

UF Chile ships twice: an Android app in Kotlin and a web app in TypeScript.
They are two implementations, not one codebase compiled twice, so they can
drift. This file says what is allowed to drift, what is not, and what to do
when a feature arrives.

## The line

**The numbers may never differ.** Everything a user could quote, screenshot or
act on has to be identical on both channels: the UF for a given day, a peso
conversion, an inflation equivalence, a dividend, a CAE, an amortisation row.
Someone comparing the phone against the laptop and finding two different
figures has no reason to trust either.

**The interface is allowed to differ**, where the platform is genuinely
different. A bottom tab bar on a phone and a rail on a laptop are the same
navigation. An install card belongs on the web and is meaningless on Android.
A copy-instead-of-share fallback exists because desktop browsers have no share
sheet, and Android needs no such thing.

Everything in between - what a field is called, what a card explains, what a
button says - should match unless there is a reason, and the reason belongs in
the commit message.

## What enforces this

### Golden vectors, `shared/golden/`

The load-bearing piece. `mortgage.json` holds inputs and the exact expected
outputs at the engines' own scale. Both suites read it:

- `app/src/test/java/cl/ufchile/app/parity/GoldenVectorTest.kt`
- `web/src/domain/__tests__/goldenVectors.test.ts`

Neither side generates the file. It is the contract, and both must agree with
it exactly - string equality of the decimal representation, not a tolerance.
Changing the engine on purpose means changing the fixture once, and then both
suites confirm the change landed on both platforms.

Adding a case is cheap and always worth it. Every reported miscalculation
should arrive as a new case before it is fixed.

### `shared/uf_daily.txt`, by way of the Android assets

The daily series has one file, `app/src/main/assets/uf_daily.txt`, regenerated
by `refresh-data.yml` and copied into the web build by `web/scripts/sync-data.mjs`.
Neither channel has its own copy to fall behind.

### CI, `.github/workflows/checks.yml`

Runs both suites on the same commit. This is what makes the golden vectors
matter: without it the Kotlin half only runs when someone remembers.

## What to do when a feature arrives

1. **Decide where the logic lives.** Anything that computes a number belongs in
   `domain/`, on both sides, and gets a golden case. Anything that only draws
   is UI and gets written twice, natively.

2. **Land both channels in the same commit.** Not "web now, Android later":
   later is how the versions came apart in the first place. If one platform
   genuinely cannot have it, say so in the commit message and add a line to the
   table below.

3. **Copy the words, not just the behaviour.** A field called "Pesos (al 8 de
   septiembre)" on one channel and "Pesos" on the other is drift, and it is the
   kind users notice first.

4. **Keep the version in step.** `app/build.gradle.kts` (`versionName`) and
   `web/package.json` (`version`) are the same string. A release bumps both.

## Deliberately one-sided

| Feature | Channel | Why |
| --- | --- | --- |
| Install / home-screen card | Web | Android is installed by definition. |
| Collapsible navigation rail, two-column layout | Web | A phone has one column and a bottom bar; this is the same navigation at a different size. |
| Copy-to-clipboard fallback for sharing | Web | Android always has a share sheet; desktop browsers often have none. |
| Offline service worker | Web | The Android app bundles its data and its code already. |
| Background refresh (WorkManager) | Android | The web app fetches when it is opened. |
| Room database for saved simulations | Android | The web app uses localStorage, same data, same shape. |

Anything not in this table is expected on both.
