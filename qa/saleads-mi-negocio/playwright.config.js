// @ts-check
const { defineConfig, devices } = require("@playwright/test");

const baseURL = process.env.SALEADS_LOGIN_URL;

module.exports = defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  timeout: 180_000,
  expect: {
    timeout: 20_000,
  },
  retries: 0,
  reporter: [["line"], ["html", { open: "never" }]],
  outputDir: "test-results",
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
