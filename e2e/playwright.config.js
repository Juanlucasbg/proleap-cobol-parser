const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 10 * 60 * 1000,
  expect: {
    timeout: 15_000
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  outputDir: "test-results",
  use: {
    baseURL: process.env.SALEADS_BASE_URL || undefined,
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "off",
    viewport: { width: 1440, height: 900 }
  }
});
