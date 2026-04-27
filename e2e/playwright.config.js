const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 8 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  retries: process.env.CI ? 1 : 0,
  fullyParallel: false,
  reporter: [
    ["list"],
    ["json", { outputFile: "artifacts/reports/playwright-results.json" }],
    ["html", { outputFolder: "artifacts/reports/html-report", open: "never" }],
  ],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 60 * 1000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
