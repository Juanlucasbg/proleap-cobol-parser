const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.SALEADS_START_URL || process.env.BASE_URL,
    trace: "on-first-retry",
    viewport: { width: 1440, height: 900 },
  },
});
