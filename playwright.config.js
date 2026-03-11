const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  retries: 0,
  workers: 1,
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
