const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 4 * 60 * 1000,
  expect: {
    timeout: 15 * 1000
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADED !== "true",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    trace: "retain-on-failure",
    video: "off",
    screenshot: "off"
  }
});
