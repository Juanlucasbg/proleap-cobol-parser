import { defineConfig } from "@playwright/test";

const isCI = !!process.env.CI;
const configuredBaseUrl = process.env.SALEADS_URL || process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 120_000,
  expect: {
    timeout: 20_000,
  },
  fullyParallel: false,
  retries: isCI ? 1 : 0,
  workers: 1,
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
  ],
  use: {
    baseURL: configuredBaseUrl,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    headless: process.env.HEADED ? false : true,
  },
});
