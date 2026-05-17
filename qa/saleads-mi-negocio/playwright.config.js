// @ts-check
const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 20 * 1000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || undefined,
    headless: process.env.HEADLESS !== "false",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
