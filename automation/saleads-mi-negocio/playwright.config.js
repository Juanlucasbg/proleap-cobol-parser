// @ts-check
const { defineConfig } = require("@playwright/test");

const baseURL = process.env.SALEADS_LOGIN_URL || undefined;

module.exports = defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: 0,
  workers: 1,
  timeout: 180000,
  expect: {
    timeout: 10000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    actionTimeout: 20000,
    navigationTimeout: 60000,
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
