import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: 0,
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["json", { outputFile: "artifacts/playwright-results.json" }]
  ],
  outputDir: "test-results",
  use: {
    baseURL,
    headless: process.env.HEADLESS ? process.env.HEADLESS !== "false" : true,
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    actionTimeout: 30000,
    navigationTimeout: 60000
  }
});
