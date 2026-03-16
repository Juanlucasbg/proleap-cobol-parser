const { defineConfig } = require("@playwright/test");

const baseURL = process.env.SALEADS_BASE_URL || process.env.BASE_URL;

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  outputDir: "test-results",
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15000,
    navigationTimeout: 45000,
  },
});
