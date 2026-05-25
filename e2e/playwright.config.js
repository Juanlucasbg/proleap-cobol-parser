const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 10 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  outputDir: "test-results",
  use: {
    viewport: { width: 1440, height: 900 },
    actionTimeout: 30 * 1000,
    navigationTimeout: 60 * 1000,
    ignoreHTTPSErrors: true,
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "off",
  },
});
