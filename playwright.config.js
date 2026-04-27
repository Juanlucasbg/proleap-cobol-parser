const { defineConfig } = require("@playwright/test");

const headless = process.env.HEADLESS !== "false";

module.exports = defineConfig({
  testDir: "./e2e/tests",
  timeout: 120000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  use: {
    headless,
    viewport: { width: 1536, height: 960 },
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
    actionTimeout: 30000,
    navigationTimeout: 60000
  }
});
