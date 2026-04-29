const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240000,
  expect: {
    timeout: 15000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
  ],
  use: {
    headless: process.env.HEADLESS !== "false",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "off",
    actionTimeout: 30000,
    navigationTimeout: 60000,
  },
});
