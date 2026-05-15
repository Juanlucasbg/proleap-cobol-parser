const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  fullyParallel: false,
  reporter: [["list"], ["html", { open: "never" }]],
  outputDir: "./test-results",
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
