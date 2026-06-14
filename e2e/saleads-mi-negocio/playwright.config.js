const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240_000,
  expect: {
    timeout: 15_000,
  },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "on-first-retry",
    video: "retain-on-failure",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
});
