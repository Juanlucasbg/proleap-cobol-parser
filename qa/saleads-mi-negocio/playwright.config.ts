import { defineConfig } from "@playwright/test";

const startUrl = process.env.SALEADS_START_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 180000,
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { outputFolder: "artifacts/html-report", open: "never" }]],
  use: {
    baseURL: startUrl,
    headless: process.env.PW_HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    trace: "on-first-retry"
  }
});
