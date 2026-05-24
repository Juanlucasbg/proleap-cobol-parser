const { defineConfig } = require("@playwright/test");
const dotenv = require("dotenv");

dotenv.config();

const configuredBaseUrl =
  process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 4 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: configuredBaseUrl,
    headless: process.env.HEADED === "true" ? false : true,
    viewport: { width: 1440, height: 900 },
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
