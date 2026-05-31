const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240000,
  expect: {
    timeout: 20000,
  },
  workers: 1,
  reporter: [
    ["list"],
    ["json", { outputFile: "playwright-report/results.json" }],
  ],
  use: {
    headless: process.env.HEADED ? false : true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 30000,
    navigationTimeout: 45000,
    trace: "retain-on-failure",
    screenshot: "off",
    video: "off",
  },
});
