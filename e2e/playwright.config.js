const { defineConfig } = require("@playwright/test");

const baseURL =
  process.env.SALEADS_START_URL ||
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL;

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 10 * 60 * 1000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: [["html", { open: "never" }], ["list"]],
  use: {
    baseURL,
    headless: process.env.HEADED ? false : true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
  },
  outputDir: "test-results",
});
