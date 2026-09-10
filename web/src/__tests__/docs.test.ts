import { describe, expect, it } from "vitest";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { basename, dirname, join, posix, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/**
 * The README is the front door, and a front door that describes a different
 * house is worse than no door at all. Prose cannot be tested, but the facts
 * around it can: the version, the counts, the links, the screenshots and the
 * tabs the app actually ships. Each of those has drifted before.
 *
 * This runs in `npm test`, which CI runs on every push and every pull request,
 * so a change that outdates the README turns the build red in the same commit
 * that made it. When one of these fails, the fix is to update the document,
 * never to relax the check.
 *
 * See CLAUDE.md for the rule this enforces.
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
const read = (path: string): string => readFileSync(join(ROOT, path), "utf8");
const exists = (path: string): boolean => {
  try {
    statSync(join(ROOT, path));
    return true;
  } catch {
    return false;
  }
};

/** Every markdown file that has to stay true, the README first. */
const DOCS = [
  "README.md",
  "CLAUDE.md",
  "shared/PARITY.md",
  ...readdirSync(join(ROOT, "docs"))
    .filter((name) => name.endsWith(".md"))
    .map((name) => `docs/${name}`),
];

const walk = (dir: string, ext: string): string[] =>
  readdirSync(join(ROOT, dir), { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory()
      ? walk(`${dir}/${entry.name}`, ext)
      : entry.name.endsWith(ext) ? [`${dir}/${entry.name}`] : [],
  );

/**
 * Suites that write one test per fixture case inside a loop, so the source
 * cannot be counted by reading it: the real number comes from the fixture.
 * Each entry replaces its file's single looped call with that many tests.
 *
 * A new suite that loops without saying so here makes the totals below wrong
 * and fails this file, which is the intended way to find out.
 */
const GENERATED: Record<string, () => number> = {
  "goldenVectors.test.ts": () =>
    (JSON.parse(read("shared/golden/mortgage.json")) as { cases: unknown[] }).cases.length,
};

// A Kotlin test is an @Test annotation on its own line; a web test is an it()
// or test() opening a statement. Commented-out ones start with a slash and are
// therefore not counted, which is the intent.
const androidTests = (file: string): number =>
  (read(file).match(/^[ \t]*@Test\b/gm) ?? []).length;
const webTests = (file: string): number => {
  const written = (read(file).match(/^[ \t]*(?:it|test)[ \t]*\(/gm) ?? []).length;
  const generated = GENERATED[basename(file)];
  return generated === undefined ? written : written - 1 + generated();
};

const ANDROID_UNIT = walk("app/src/test", ".kt");
const ANDROID_UI = walk("app/src/androidTest", ".kt");
const WEB = walk("web/src", ".test.ts");

const sum = (files: string[], count: (f: string) => number): number =>
  files.reduce((total, f) => total + count(f), 0);

/**
 * The rows of the layer table in docs/TESTING.md, and the suites each one
 * counts. Keeping the mapping here rather than in the prose is what lets the
 * table be checked: a new test file that fits no row fails the last test in
 * this file until someone decides where it belongs.
 */
const LAYERS: { row: string; android: string[]; web: string[] }[] = [
  {
    row: "Pure calculation",
    android: ["MortgageEngineTest", "UfEngineTest", "UfReajusteTest", "UfLookupTest", "UfSanityTest"],
    web: ["mortgageEngine", "ufEngine", "ufReajuste", "ufLookup", "ufSanity"],
  },
  {
    row: "Parity with the other channel",
    android: ["GoldenVectorTest", "UfGoldenVectorTest", "BtcGoldenVectorTest", "ConverterGoldenVectorTest"],
    web: ["goldenVectors", "ufGoldenVectors", "btcGoldenVectors", "converterGoldenVectors", "parity"],
  },
  {
    row: "Formatting and input",
    android: ["FormatTest", "ThousandsTransformationTest", "NumericDefaultsTest"],
    web: ["format"],
  },
  {
    row: "Data contracts and assets",
    android: ["DtoParsingTest", "UfDailySeedParseTest", "UfSeedTest"],
    web: ["seed", "usdSeed"],
  },
  { row: "Persistence", android: ["DatabaseCrudTest", "UfDaoTest"], web: [] },
  { row: "Share text", android: ["ShareReajusteTest"], web: [] },
  { row: "Documentation", android: [], web: ["docs"] },
  { row: "UI flows", android: ["NavigationSmokeTest"], web: [] },
];

/** The two counts a row of that table declares; null where the cell is blank. */
const declaredBy = (row: string): { android: number | null; web: number | null } => {
  const line = read("docs/TESTING.md")
    .split("\n")
    .find((l) => l.startsWith(`| ${row} |`));
  expect(line, `no row "${row}" in the layer table of docs/TESTING.md`).toBeDefined();
  const cells = line!.split("|").map((c) => c.trim());
  const number = (cell: string): number | null => {
    const found = /(\d+)/.exec(cell);
    return found === null ? null : Number(found[1]);
  };
  return { android: number(cells[2] ?? ""), web: number(cells[3] ?? "") };
};

/** GitHub's heading slug, near enough for the anchors these documents use. */
const anchorsOf = (markdown: string): Set<string> =>
  new Set(
    [...markdown.matchAll(/^#+\s+(.*)$/gm)].map(([, heading]) =>
      (heading ?? "")
        .toLowerCase()
        .replace(/[^\w\- ]/g, "")
        .trim()
        .replace(/\s+/g, "-"),
    ),
  );

const linksOf = (markdown: string): string[] =>
  [...markdown.matchAll(/\]\(([^)\s]+)\)|src="([^"]+)"|href="([^"]+)"/g)]
    .map((m) => m[1] ?? m[2] ?? m[3] ?? "")
    .filter((link) => !/^(https?:|mailto:|#)/.test(link));

describe("the documentation describes the app that exists", () => {
  it("gives the version both manifests declare", () => {
    const gradle = /versionName\s*=\s*"([^"]+)"/.exec(read("app/build.gradle.kts"))?.[1];
    expect(gradle, "versionName not found in app/build.gradle.kts").toBeDefined();

    expect(
      read("README.md"),
      `the README should say Version **${gradle}**`,
    ).toContain(`Version **${gradle}**`);
  });

  it("links every document, and every document it links exists", () => {
    const readme = read("README.md");
    for (const doc of DOCS) {
      if (doc === "README.md") continue;
      expect(readme, `the README should link ${doc}`).toContain(doc);
    }
  });

  it("resolves every relative link and anchor it makes", () => {
    const broken: string[] = [];
    for (const doc of DOCS) {
      const from = posix.dirname(doc);
      for (const link of linksOf(read(doc))) {
        const [target = "", fragment] = link.split("#");
        const path = target === "" ? doc : posix.normalize(posix.join(from, decodeURI(target)));
        if (!exists(path)) {
          broken.push(`${doc} → ${link} (no such file)`);
        } else if (fragment !== undefined && path.endsWith(".md") && !anchorsOf(read(path)).has(fragment)) {
          broken.push(`${doc} → ${link} (no such heading)`);
        }
      }
    }
    expect(broken).toEqual([]);
  });

  it("shows every screenshot that is kept, and keeps every one it shows", () => {
    const readme = read("README.md");
    const kept = readdirSync(join(ROOT, "docs/screenshots")).filter((f) => f.endsWith(".png"));

    expect(kept.length, "docs/screenshots is empty").toBeGreaterThan(0);
    for (const shot of kept) {
      expect(
        readme,
        `docs/screenshots/${shot} is not shown in the README; show it or delete it`,
      ).toContain(shot);
    }
  });

  it("names every tab the app ships", () => {
    const app = read("web/src/ui/App.tsx");
    const groups = /const GROUPS = \[([\s\S]*?)\n\] as const;/.exec(app)?.[1];
    expect(groups, "GROUPS not found in web/src/ui/App.tsx").toBeDefined();

    const labels = [...groups!.matchAll(/label:\s*"([^"]+)"/g)].map(([, l]) => l!);
    expect(labels.length, "no tab labels found").toBeGreaterThan(0);

    const readme = read("README.md");
    for (const label of labels) {
      expect(readme, `the README never mentions the "${label}" tab`).toContain(label);
    }
  });

  it("states the number of tests each suite actually has", () => {
    const counts = {
      "JVM tests": sum(ANDROID_UNIT, androidTests),
      "instrumented tests": sum(ANDROID_UI, androidTests),
      tests: sum(WEB, webTests),
    };

    for (const [noun, count] of Object.entries(counts)) {
      for (const doc of ["README.md", "docs/TESTING.md", "CLAUDE.md"]) {
        expect(read(doc), `${doc} should say "${count} ${noun}"`).toContain(`${count} ${noun}`);
      }
    }
  });

  it("accounts for every test file in the layer table", () => {
    const claimed = (names: string[], files: string[], suffix: string): string[] =>
      files.filter((f) => names.some((n) => basename(f) === `${n}${suffix}`));

    const seen = new Set<string>();
    for (const layer of LAYERS) {
      const android = claimed(layer.android, ANDROID_UNIT.concat(ANDROID_UI), ".kt");
      const web = claimed(layer.web, WEB, ".test.ts");
      for (const f of android.concat(web)) seen.add(f);

      const declared = declaredBy(layer.row);
      expect(
        declared.android,
        `docs/TESTING.md row "${layer.row}" (Android)`,
      ).toBe(android.length === 0 ? null : sum(android, androidTests));
      expect(
        declared.web,
        `docs/TESTING.md row "${layer.row}" (Web)`,
      ).toBe(web.length === 0 ? null : sum(web, webTests));
    }

    const unclassified = ANDROID_UNIT.concat(ANDROID_UI, WEB).filter((f) => !seen.has(f));
    expect(
      unclassified,
      "add these to LAYERS here and to the layer table in docs/TESTING.md",
    ).toEqual([]);
  });
});
