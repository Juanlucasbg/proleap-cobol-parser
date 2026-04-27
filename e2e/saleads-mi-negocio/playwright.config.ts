import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 20_000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
    ["json", { outputFile: "artifacts/playwright-results.json" }],
  ],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
