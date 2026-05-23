const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    browserName: "chromium",
    headless: true,
    baseURL: process.env.SALEADS_BASE_URL || undefined,
    trace: "on-first-retry",
    video: "retain-on-failure",
  },
});
