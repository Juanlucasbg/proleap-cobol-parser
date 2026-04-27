const { defineConfig } = require("@playwright/test");
const path = require("path");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 120000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["json", { outputFile: "e2e-artifacts/reports/mi-negocio-playwright-report.json" }]
  ],
  use: {
    baseURL: process.env.SALEADS_BASE_URL || undefined,
    headless: process.env.HEADLESS === "false" ? false : true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 }
  },
  outputDir: path.join("test-results")
});
