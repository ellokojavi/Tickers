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
    (JSON.parse(read("web/golden/mortgage.json")) as { cases: unknown[] }).cases.length,
};

// A test is an it() or test() opening a statement. Commented-out ones start
// with a slash and are therefore not counted, which is the intent.
const webTests = (file: string): number => {
  const written = (read(file).match(/^[ \t]*(?:it|test)[ \t]*\(/gm) ?? []).length;
  const generated = GENERATED[basename(file)];
  return generated === undefined ? written : written - 1 + generated();
};

const WEB = walk("web/src", ".test.ts");

const sum = (files: string[], count: (f: string) => number): number =>
  files.reduce((total, f) => total + count(f), 0);

/**
 * The rows of the layer table in docs/TESTING.md, and the suites each one
 * counts. Keeping the mapping here rather than in the prose is what lets the
 * table be checked: a new test file that fits no row fails the last test in
 * this file until someone decides where it belongs.
 */
const LAYERS: { row: string; web: string[] }[] = [
  {
    row: "Pure calculation",
    web: ["mortgageEngine", "ufEngine", "ufReajuste", "ufLookup", "ufSanity"],
  },
  {
    row: "Pinned expectations",
    web: ["goldenVectors", "ufGoldenVectors", "btcGoldenVectors", "converterGoldenVectors"],
  },
  { row: "Formatting and input", web: ["format"] },
  { row: "Data contracts and assets", web: ["seed", "usdSeed"] },
  { row: "Navigation", web: ["route"] },
  { row: "Documentation", web: ["docs"] },
];

/** The count a row of that table declares. */
const declaredBy = (row: string): number | null => {
  const line = read("docs/TESTING.md")
    .split("\n")
    .find((l) => l.startsWith(`| ${row} |`));
  expect(line, `no row "${row}" in the layer table of docs/TESTING.md`).toBeDefined();
  const cells = line!.split("|").map((c) => c.trim());
  const found = /(\d+)/.exec(cells[2] ?? "");
  return found === null ? null : Number(found[1]);
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
  it("gives the version the manifest declares", () => {
    const version = (JSON.parse(read("web/package.json")) as { version: string }).version;

    expect(
      read("README.md"),
      `the README should say Version **${version}**`,
    ).toContain(`Version **${version}**`);
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

  it("states the number of tests the suite actually has", () => {
    const count = sum(WEB, webTests);

    for (const doc of ["README.md", "docs/TESTING.md", "CLAUDE.md"]) {
      expect(read(doc), `${doc} should say "${count} tests"`).toContain(`${count} tests`);
    }
  });

  it("accounts for every test file in the layer table", () => {
    const seen = new Set<string>();
    for (const layer of LAYERS) {
      const files = WEB.filter((f) => layer.web.some((n) => basename(f) === `${n}.test.ts`));
      for (const f of files) seen.add(f);

      expect(
        declaredBy(layer.row),
        `docs/TESTING.md row "${layer.row}"`,
      ).toBe(files.length === 0 ? null : sum(files, webTests));
    }

    const unclassified = WEB.filter((f) => !seen.has(f));
    expect(
      unclassified,
      "add these to LAYERS here and to the layer table in docs/TESTING.md",
    ).toEqual([]);
  });
});
