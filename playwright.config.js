// @ts-check
const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"]],
  use: {
    actionTimeout: 30000,
    navigationTimeout: 60000,
    trace: "on-first-retry",
    video: "off",
    screenshot: "off",
    headless: process.env.PW_HEADLESS !== "false"
  }
});
