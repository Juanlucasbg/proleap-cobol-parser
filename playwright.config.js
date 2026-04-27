const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests/e2e",
  timeout: 120000,
  expect: {
    timeout: 15000,
  },
  retries: 0,
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
  ],
  use: {
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    ignoreHTTPSErrors: true,
    actionTimeout: 20000,
    navigationTimeout: 60000,
  },
});
