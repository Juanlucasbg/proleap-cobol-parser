const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 120000,
  expect: {
    timeout: 15000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    viewport: { width: 1600, height: 1000 },
    ignoreHTTPSErrors: true,
    trace: "on-first-retry",
    video: "retain-on-failure"
  }
});
