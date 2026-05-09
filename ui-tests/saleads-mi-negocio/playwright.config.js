const { defineConfig, devices } = require("@playwright/test");
const path = require("path");

const defaultTimeoutMs = Number(process.env.SALEADS_TIMEOUT_MS || 30000);
const outputDir = process.env.SALEADS_ARTIFACTS_DIR || path.join(__dirname, "artifacts");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: defaultTimeoutMs * 2,
  expect: {
    timeout: defaultTimeoutMs
  },
  retries: 0,
  reporter: [["list"], ["html", { outputFolder: path.join(outputDir, "html-report"), open: "never" }]],
  outputDir: path.join(outputDir, "test-results"),
  use: {
    actionTimeout: defaultTimeoutMs,
    navigationTimeout: defaultTimeoutMs,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ]
});
