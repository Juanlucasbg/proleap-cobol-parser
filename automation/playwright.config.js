// @ts-check
const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: ".",
  testMatch: "saleads_mi_negocio_full_test.spec.js",
  timeout: 8 * 60 * 1000,
  retries: process.env.CI ? 1 : 0,
  use: {
    headless: process.env.HEADLESS ? process.env.HEADLESS !== "false" : true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  reporter: [["list"], ["json", { outputFile: "results/playwright-report.json" }]],
});
