const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: ".",
  testMatch: ["*.spec.js"],
  timeout: 180000,
  expect: {
    timeout: 20000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 30000,
    navigationTimeout: 45000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure"
  }
});
