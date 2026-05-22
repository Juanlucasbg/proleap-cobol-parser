const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }]
  ],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15000,
    navigationTimeout: 45000,
    baseURL: process.env.SALEADS_BASE_URL || undefined,
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
