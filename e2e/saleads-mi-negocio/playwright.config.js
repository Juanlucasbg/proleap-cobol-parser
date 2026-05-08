const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 300000,
  expect: {
    timeout: 15000,
  },
  reporter: [
    ["list"],
    ["json", { outputFile: "artifacts/playwright-results.json" }],
  ],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 30000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "off",
  },
});
