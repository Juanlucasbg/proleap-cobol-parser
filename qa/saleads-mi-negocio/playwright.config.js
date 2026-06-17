const { defineConfig } = require("@playwright/test");

const baseURL = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
  },
});
