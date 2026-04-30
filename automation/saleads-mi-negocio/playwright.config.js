const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 120000,
  retries: 0,
  use: {
    headless: process.env.HEADED === "true" ? false : true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15000,
    navigationTimeout: 45000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure"
  },
  reporter: [["list"]]
});
