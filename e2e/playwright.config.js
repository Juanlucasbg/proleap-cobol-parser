const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 6 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    headless: process.env.HEADLESS === "true",
    viewport: { width: 1440, height: 900 },
    screenshot: "off",
    trace: "on-first-retry",
    video: "off",
  },
});
