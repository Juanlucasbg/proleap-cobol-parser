const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 10000,
  },
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.PW_HEADLESS !== "false",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
    baseURL: process.env.SALEADS_LOGIN_URL || undefined,
  },
});
