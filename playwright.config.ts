import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 120_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1600, height: 1000 },
    actionTimeout: 20_000,
    navigationTimeout: 40_000,
    screenshot: "only-on-failure",
    trace: "on-first-retry",
  },
});
