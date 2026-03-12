const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 8 * 60 * 1000,
  expect: {
    timeout: 20000
  },
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    screenshot: "off",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  },
  reporter: [
    ["list"],
    ["html", { outputFolder: "artifacts/playwright-report", open: "never" }]
  ]
});
