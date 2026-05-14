const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  fullyParallel: false,
  workers: 1,
  reporter: [
    ["list"],
    ["json", { outputFile: "artifacts/playwright-results.json" }],
    ["html", { outputFolder: "artifacts/html-report", open: "never" }]
  ],
  use: {
    baseURL: process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || undefined,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1600, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 60_000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure"
  }
});
