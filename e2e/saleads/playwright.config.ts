import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 15_000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    trace: "retain-on-failure",
    video: "retain-on-failure"
  },
  outputDir: "./test-results"
});
