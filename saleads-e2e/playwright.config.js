const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 120000,
  expect: {
    timeout: 15000,
  },
  reporter: [["list"], ["html", { outputFolder: "artifacts/playwright-report", open: "never" }]],
  use: {
    headless: true,
    viewport: { width: 1600, height: 1000 },
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    trace: "retain-on-failure",
  },
});
