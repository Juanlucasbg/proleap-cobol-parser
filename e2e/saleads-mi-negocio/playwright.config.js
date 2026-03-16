const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  reporter: [["list"]],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
  },
});
