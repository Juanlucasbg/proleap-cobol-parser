import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 20_000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.SALEADS_LOGIN_URL || process.env.PLAYWRIGHT_BASE_URL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 60_000,
    screenshot: "only-on-failure",
    trace: "on-first-retry",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
