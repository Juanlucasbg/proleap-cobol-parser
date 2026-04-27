const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    trace: "on-first-retry",
    screenshot: "off",
    video: "retain-on-failure",
    actionTimeout: 30000,
    navigationTimeout: 60000,
    baseURL: process.env.SALEADS_BASE_URL,
  },
});
