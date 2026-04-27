const { defineConfig, devices } = require("@playwright/test");
const path = require("path");

const artifactsDir = path.resolve(__dirname, "artifacts");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 8 * 60 * 1000,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { outputFolder: path.join(artifactsDir, "playwright-report") }]],
  use: {
    baseURL: process.env.SALEADS_URL || undefined,
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
    navigationTimeout: 45_000,
    actionTimeout: 20_000,
    viewport: { width: 1440, height: 900 }
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ]
});
