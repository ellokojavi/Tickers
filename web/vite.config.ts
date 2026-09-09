import { readFileSync } from "node:fs";
import { defineConfig } from "vite";
import preact from "@preact/preset-vite";

const version = JSON.parse(readFileSync("./package.json", "utf8")).version as string;

export default defineConfig({
  define: { __APP_VERSION__: JSON.stringify(version) },
  plugins: [preact()],
  // Served from https://<user>.github.io/UFChile/
  base: "/UFChile/",
  build: { target: "es2022" },
  test: { environment: "node" },
});
