import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 15 * 1000
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.SALEADS_LOGIN_URL,
    headless: process.env.PW_HEADLESS === "false" ? false : true,
    viewport: { width: 1600, height: 900 },
    actionTimeout: 15 * 1000,
    navigationTimeout: 30 * 1000,
    screenshot: "off",
    trace: "on-first-retry"
  }
});
