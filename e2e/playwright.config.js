const { defineConfig } = require("@playwright/test");

const baseURL =
  process.env.SALEADS_URL ||
  process.env.PLAYWRIGHT_BASE_URL ||
  process.env.BASE_URL;

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 20000,
  },
  fullyParallel: false,
  retries: 1,
  workers: 1,
  reporter: [
    ["line"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
  ],
  use: {
    baseURL,
    browserName: "chromium",
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    ignoreHTTPSErrors: true,
    screenshot: "only-on-failure",
    video: "on-first-retry",
    trace: "on-first-retry",
  },
});
