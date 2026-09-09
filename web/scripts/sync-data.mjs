// The UF series has one source of truth: the file the Android app ships.
// Copying it at build time means the daily refresh workflow updates both
// platforms from the same commit, and neither can drift from the other.
import { copyFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = resolve(here, "../../app/src/main/assets/uf_daily.txt");
const target = resolve(here, "../public/uf_daily.txt");

if (!existsSync(source)) {
  console.error(`Dataset not found at ${source}`);
  process.exit(1);
}
mkdirSync(dirname(target), { recursive: true });
copyFileSync(source, target);
console.log(`uf_daily.txt copied from the Android assets`);
