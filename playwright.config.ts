import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 15 * 1000
  },
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 }
  },
  reporter: [["list"], ["html", { open: "never" }]]
});
