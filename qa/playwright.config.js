const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  retries: 0,
  reporter: [
    ["list"],
    ["json", { outputFile: "artifacts/playwright-report.json" }]
  ],
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "off"
  },
  outputDir: "artifacts/test-results"
});
