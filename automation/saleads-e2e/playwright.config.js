const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 30000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    trace: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 30000,
    navigationTimeout: 60000,
  },
});
