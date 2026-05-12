const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 300000,
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    actionTimeout: 30000,
    navigationTimeout: 60000,
    trace: "on-first-retry",
    video: "retain-on-failure"
  }
});
