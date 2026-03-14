const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e/tests",
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never" }],
    ["json", { outputFile: "e2e/artifacts/playwright-report.json" }],
  ],
  use: {
    headless: process.env.HEADLESS !== "false",
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    viewport: { width: 1440, height: 900 },
    screenshot: "off",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    baseURL:
      process.env.SALEADS_LOGIN_URL ||
      process.env.SALEADS_BASE_URL ||
      process.env.BASE_URL ||
      process.env.APP_URL ||
      undefined,
  },
});
