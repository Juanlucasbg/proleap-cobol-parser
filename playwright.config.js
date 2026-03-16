const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e",
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  outputDir: "test-results",
  use: {
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
    viewport: { width: 1440, height: 900 }
  }
});
