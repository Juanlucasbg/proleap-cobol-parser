const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests/e2e",
  timeout: 120000,
  expect: {
    timeout: 15000,
  },
  retries: 0,
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    headless: process.env.PW_HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15000,
    navigationTimeout: 30000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
});
