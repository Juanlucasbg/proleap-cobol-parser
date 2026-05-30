const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e",
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 30000,
    trace: "on-first-retry",
    video: "retain-on-failure",
    screenshot: "only-on-failure"
  }
});
