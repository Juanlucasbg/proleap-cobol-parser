const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  workers: 1,
  timeout: 300000,
  reporter: [["list"]],
  use: {
    headless: process.env.HEADLESS !== "false",
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 30000,
    navigationTimeout: 60000,
    screenshot: "off",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
