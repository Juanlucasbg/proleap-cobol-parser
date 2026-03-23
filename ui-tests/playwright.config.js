const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.SALEADS_URL || process.env.BASE_URL,
    actionTimeout: 20000,
    navigationTimeout: 45000,
    headless: process.env.HEADLESS !== "false",
    locale: "es-ES",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
