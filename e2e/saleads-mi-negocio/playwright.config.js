const { defineConfig } = require("@playwright/test");
const path = require("path");

const outputDir = path.join(__dirname, "artifacts");

module.exports = defineConfig({
  testDir: path.join(__dirname, "tests"),
  fullyParallel: false,
  retries: 0,
  workers: 1,
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  reporter: [["list"], ["html", { outputFolder: path.join(outputDir, "html-report"), open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    screenshot: "off",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    actionTimeout: 20000,
    navigationTimeout: 45000
  },
  outputDir: path.join(outputDir, "test-results")
});
