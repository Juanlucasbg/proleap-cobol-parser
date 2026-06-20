const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 15 * 1000
  },
  retries: 1,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "artifacts/html-report" }]
  ],
  use: {
    baseURL: process.env.SALEADS_BASE_URL || process.env.BASE_URL,
    headless: process.env.HEADED === "true" ? false : true,
    trace: "on-first-retry",
    video: "retain-on-failure",
    screenshot: "only-on-failure"
  }
});
