const { defineConfig, devices } = require("@playwright/test");
const path = require("path");

const baseURL = process.env.SALEADS_BASE_URL || process.env.BASE_URL;

module.exports = defineConfig({
  testDir: path.join(__dirname, "tests"),
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: path.join(__dirname, "playwright-report") }]
  ],
  outputDir: path.join(__dirname, "test-results"),
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15000,
    navigationTimeout: 45000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ]
});
