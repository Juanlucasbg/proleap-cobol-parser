import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [["line"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    trace: "on-first-retry",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
    actionTimeout: 20000,
    navigationTimeout: 45000,
    baseURL: process.env.SALEADS_BASE_URL,
  },
});
