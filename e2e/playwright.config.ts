import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 120_000,
  expect: {
    timeout: 15_000
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
    headless: process.env.HEADLESS !== "false"
  }
});
