const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 20000,
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    browserName: "chromium",
    headless: true,
    actionTimeout: 15000,
    navigationTimeout: 45000,
    screenshot: "off",
    trace: "retain-on-failure",
    video: "off",
    viewport: { width: 1440, height: 900 },
  },
});
