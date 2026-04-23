const { defineConfig, devices } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 300000,
  expect: {
    timeout: 15000
  },
  reporter: [
    ["list"],
    ["json", { outputFile: "test-results/playwright-report.json" }]
  ],
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    actionTimeout: 20000,
    navigationTimeout: 45000
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ],
  outputDir: "test-results/artifacts"
});
