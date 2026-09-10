// Retake the screenshots the README shows.
//
// The README is the front door, and stale screenshots are the part of it that
// goes wrong most quietly: nothing fails, the pictures just show an app that
// no longer exists. Regenerating them is therefore one command, for the same
// reason the icons and the bundled series are.
//
// One shot per tab plus the dark theme, taken from the web app at a phone's
// size, because that is the shape both channels are designed at first. The
// data comes from the running app, so what lands in docs/ is what the app
// shows today. A test checks that the README shows every file written here.
//
// Usage, with the dev server already running (npm run dev):
//
//   npm run screenshots
//
// It drives the Chrome already on the machine rather than downloading a
// browser of its own, which is why the dependency is playwright-core: CI
// installs it in a second and never opens it.
import { chromium } from "playwright-core";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const URL_ = process.env.TICKERS_URL ?? "http://localhost:5173/";
const OUT = resolve(dirname(fileURLToPath(import.meta.url)), "../../docs/screenshots");

// An iPhone 13, which is the narrow end of what the layout has to hold.
const PHONE = {
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  isMobile: true,
  hasTouch: true,
  userAgent:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15" +
    " (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1",
};

// The name each file keeps. The README shows them by these names, so adding a
// shot here means adding it there: docs.test.ts fails until both agree.
const SHOTS = [
  { name: "01-uf" },
  { name: "02-dolar", tab: "Dólar" },
  { name: "03-bitcoin", tab: "Bitcoin" },
  // Both tools are worth showing as answers rather than as empty forms, so
  // each scrolls to the card that carries the result.
  { name: "04-inflacion", tab: "Inflación", scrollTo: "main .display" },
  {
    name: "05-creditos",
    tab: "Créditos",
    open: async (page) => {
      await page.locator("button.fab").click();
      await page.waitForTimeout(800);
    },
    scrollTo: "main .display",
  },
  { name: "06-oscuro", dark: true },
];

mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ channel: "chrome" }).catch(() => {
  console.error(
    "Could not start Chrome. Install Google Chrome, or set a channel this " +
      "machine has (chromium, msedge).",
  );
  process.exit(1);
});

for (const { name, tab, dark = false, open, scrollTo } of SHOTS) {
  const context = await browser.newContext({
    ...PHONE,
    colorScheme: dark ? "dark" : "light",
    locale: "es-CL",
    timezoneId: "America/Santiago",
    // A cached shell from a previous run would photograph the previous build.
    serviceWorkers: "block",
  });
  const page = await context.newPage();

  try {
    await page.goto(URL_, { waitUntil: "networkidle" });
  } catch {
    console.error(`Nothing answering at ${URL_}. Start it with: npm run dev`);
    process.exit(1);
  }
  await page.waitForTimeout(800);

  if (tab) {
    await page.locator("nav.tabs button", { hasText: tab }).click();
    await page.waitForTimeout(1500);
  }
  if (open) await open(page);
  if (scrollTo) {
    await page.evaluate((selector) => {
      const el = document.querySelector(selector);
      if (el) window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 150);
    }, scrollTo);
    await page.waitForTimeout(300);
  }

  await page.screenshot({ path: `${OUT}/${name}.png` });
  console.log(`${name}.png`);
  await context.close();
}

await browser.close();
