const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 20_000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  outputDir: "test-results",
  use: {
    baseURL: process.env.SALEADS_LOGIN_URL || undefined,
    trace: "on-first-retry",
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true
  }
});
