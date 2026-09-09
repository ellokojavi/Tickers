import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

/**
 * The two channels are the same product at the same version, and the version
 * is declared in two files that have no reason to know about each other. This
 * is the cheapest possible guard against them coming apart.
 *
 * See shared/PARITY.md.
 */
const read = (path: string): string =>
  readFileSync(new URL(path, import.meta.url), "utf8");

describe("the two channels stay in step", () => {
  it("declares the same version on Android and on the web", () => {
    const gradle = read("../../../app/build.gradle.kts");
    const android = /versionName\s*=\s*"([^"]+)"/.exec(gradle)?.[1];
    const web = (JSON.parse(read("../../package.json")) as { version: string }).version;

    expect(android, "versionName not found in app/build.gradle.kts").toBeDefined();
    expect(`web ${web}`).toBe(`web ${android!}`);
  });
});
