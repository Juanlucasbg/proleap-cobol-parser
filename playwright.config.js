const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 600000,
  expect: {
    timeout: 15000,
  },
  retries: 0,
  use: {
    headless: true,
    actionTimeout: 15000,
    navigationTimeout: 45000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
});
