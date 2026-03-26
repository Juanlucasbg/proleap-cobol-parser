require("dotenv").config();
const { defineConfig } = require("@playwright/test");

const baseURL = process.env.SALEADS_BASE_URL || undefined;

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 20000,
  },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: [["html", { open: "never" }], ["list"]],
  use: {
    baseURL,
    headless: process.env.HEADED !== "true",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    navigationTimeout: 45000,
    actionTimeout: 20000,
  },
});
