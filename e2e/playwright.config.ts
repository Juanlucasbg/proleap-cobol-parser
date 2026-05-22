import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 20_000
  },
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    ignoreHTTPSErrors: true
  },
  reporter: [
    ["line"],
    ["html", { outputFolder: "playwright-report", open: "never" }]
  ]
});
