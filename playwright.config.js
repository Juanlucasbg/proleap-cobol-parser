const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 120_000,
  expect: {
    timeout: 15_000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never" }],
    ["json", { outputFile: "test-results/playwright-report.json" }],
  ],
  use: {
    headless: process.env.HEADLESS !== "false",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "off",
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    baseURL: process.env.SALEADS_BASE_URL || process.env.BASE_URL || undefined,
  },
});
