const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240000,
  expect: {
    timeout: 20000,
  },
  reporter: [
    ["list"],
    ["json", { outputFile: "artifacts/playwright-results.json" }],
  ],
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 90000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
