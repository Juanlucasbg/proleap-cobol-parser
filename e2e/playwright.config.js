const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240000,
  expect: {
    timeout: 20000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  workers: 1,
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    trace: "retain-on-failure",
    screenshot: "off",
    video: "off"
  }
});
