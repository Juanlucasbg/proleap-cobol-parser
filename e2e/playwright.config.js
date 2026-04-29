const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
  ],
  use: {
    baseURL: process.env.SALEADS_BASE_URL || undefined,
    headless: process.env.HEADED ? false : true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 30 * 1000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
