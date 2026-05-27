const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    baseURL: process.env.SALEADS_BASE_URL || process.env.PLAYWRIGHT_TEST_BASE_URL,
  },
});
