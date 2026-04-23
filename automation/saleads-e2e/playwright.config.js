const { defineConfig } = require("@playwright/test");

const hasBaseUrl = Boolean(process.env.SALEADS_BASE_URL || process.env.SALEADS_LOGIN_URL);
const baseURL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || undefined;

module.exports = defineConfig({
  testDir: "./e2e/tests",
  timeout: 8 * 60 * 1000,
  expect: {
    timeout: 20_000
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    ignoreHTTPSErrors: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { browserName: "chromium" }
    }
  ],
  metadata: {
    baseUrlConfigured: hasBaseUrl
  },
  outputDir: "test-results"
});
